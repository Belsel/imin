package com.imin.backend.chat;

import com.imin.backend.chat.dto.GroupChatMessageResponse;
import com.imin.backend.chat.dto.PostGroupChatMessageRequest;
import com.imin.backend.group.GroupMembership;
import com.imin.backend.group.GroupMembershipRepository;
import com.imin.backend.group.GroupRepository;
import com.imin.backend.user.User;
import com.imin.backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Owns reading and posting a group's chat messages. There is no separate
 * {@code Chat} entity (see {@link GroupChatMessage} javadoc) — every
 * operation here is just a {@code groupId}-scoped query/insert against
 * {@link GroupChatMessageRepository}.
 *
 * <p>Authorization reuses Slice 2's group membership machinery directly
 * ({@link GroupMembershipRepository}) rather than duplicating it: only a
 * current, non-banned member of a group may view or post to that group's
 * chat (spec.md Chats). Because a ban always deletes the corresponding
 * {@link com.imin.backend.group.GroupMembership} row (see
 * {@code GroupService#banMember}), a banned member has no membership row at
 * all — so a single "is this user a current member?" check
 * ({@code GroupMembershipRepository#existsByGroupIdAndUserId}) is sufficient
 * to cover both "never joined" and "was banned" cases; there is no need for a
 * separate {@link com.imin.backend.group.GroupBanRepository} lookup or any
 * banned/not-banned flag on {@link GroupChatMessage} itself. Access is
 * re-checked on every request (no caching), so a ban/unban takes effect on
 * the very next call.
 *
 * <p>Follows the same 403 convention as {@code GroupService#listMembers}
 * (members-only visibility) rather than {@code GroupService#getGroup}'s
 * 404-for-banned convention — group chat, like the member list, is a
 * "must currently be a member" gate on an already-known group id, not a
 * "does this group exist at all" lookup, so there is no "confirm you're
 * banned vs. confirm it doesn't exist" ambiguity to protect here.
 */
@Service
@RequiredArgsConstructor
public class GroupChatService {

    /** Page size for the initial (no-cursor) poll — see design.md §5 / spec.md Chats. */
    static final int INITIAL_PAGE_SIZE = 50;

    private final GroupChatMessageRepository messageRepository;
    private final GroupRepository groupRepository;
    private final GroupMembershipRepository membershipRepository;
    private final UserRepository userRepository;

    @Transactional
    public GroupChatMessageResponse postMessage(String callerEmail, Long groupId, PostGroupChatMessageRequest request) {
        User caller = findUserByEmail(callerEmail);
        requireGroupExists(groupId);
        requireCurrentMember(groupId, caller.getId());

        GroupChatMessage message = new GroupChatMessage();
        message.setGroupId(groupId);
        message.setSenderId(caller.getId());
        message.setBody(request.body());
        messageRepository.save(message);

        return GroupChatMessageResponse.from(message, caller);
    }

    /**
     * Polling read. {@code after == null} returns the most recent
     * {@link #INITIAL_PAGE_SIZE} messages (oldest-first, ready to append to);
     * {@code after != null} returns every message with {@code id > after}, in
     * chronological (ascending id) order, for incremental append by a
     * polling frontend. See {@link GroupChatMessage} javadoc for why {@code
     * id} (not a timestamp) is the cursor.
     */
    public List<GroupChatMessageResponse> getMessages(String callerEmail, Long groupId, Long after) {
        User caller = findUserByEmail(callerEmail);
        requireGroupExists(groupId);
        requireCurrentMember(groupId, caller.getId());

        List<GroupChatMessage> messages;
        if (after == null) {
            messages = messageRepository.findByGroupIdOrderByIdDesc(groupId, PageRequest.of(0, INITIAL_PAGE_SIZE));
            messages = messages.stream()
                    .sorted(Comparator.comparing(GroupChatMessage::getId))
                    .toList();
        } else {
            messages = messageRepository.findByGroupIdAndIdGreaterThanOrderByIdAsc(groupId, after);
        }

        Map<Long, User> usersById = userRepository.findAllById(
                messages.stream().map(GroupChatMessage::getSenderId).distinct().toList()
        ).stream().collect(Collectors.toMap(User::getId, u -> u));

        return messages.stream()
                .map(m -> GroupChatMessageResponse.from(m, usersById.get(m.getSenderId())))
                .toList();
    }

    /**
     * The message's own sender can delete it; any current admin of the
     * group can also delete it, including messages they didn't send
     * (spec: group-chat-moderation). A non-sender, non-admin member is
     * rejected with 403 — mirrors {@code ActivityService.requireOwnerOrAdmin}
     * exactly.
     */
    @Transactional
    public void deleteMessage(String callerEmail, Long groupId, Long messageId) {
        User caller = findUserByEmail(callerEmail);
        requireGroupExists(groupId);
        GroupChatMessage message = findMessageOr404(groupId, messageId);
        requireSenderOrAdmin(groupId, caller.getId(), message);

        messageRepository.delete(message);
    }

    private void requireSenderOrAdmin(Long groupId, Long callerUserId, GroupChatMessage message) {
        if (message.getSenderId().equals(callerUserId)) {
            return;
        }

        GroupMembership membership = membershipRepository.findByGroupIdAndUserId(groupId, callerUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not a member of this group"));
        if (!membership.isAdmin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the message's sender or a group admin can do this");
        }
    }

    private GroupChatMessage findMessageOr404(Long groupId, Long messageId) {
        GroupChatMessage message = messageRepository.findById(messageId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Message not found"));
        if (!message.getGroupId().equals(groupId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Message not found");
        }
        return message;
    }

    private void requireGroupExists(Long groupId) {
        if (!groupRepository.existsById(groupId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Group not found");
        }
    }

    private void requireCurrentMember(Long groupId, Long userId) {
        if (!membershipRepository.existsByGroupIdAndUserId(groupId, userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not a member of this group");
        }
    }

    private User findUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }
}
