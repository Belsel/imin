package com.imin.backend.chat;

import com.imin.backend.chat.dto.DirectMessageResponse;
import com.imin.backend.chat.dto.DirectThreadResponse;
import com.imin.backend.chat.dto.PostDirectMessageRequest;
import com.imin.backend.social.BlockRepository;
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
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Owns direct (1:1) chat between two users: thread lookup-or-create,
 * sending, and cursor-based polling. Mirrors {@link com.imin.backend.chat}'s
 * group-chat conventions exactly (same {@code id}-cursor polling pattern as
 * {@link com.imin.backend.chat.GroupChatService}; see that class's javadoc
 * for why {@code id}, not a timestamp, is the cursor).
 *
 * <p><strong>No friend-gating, anywhere.</strong> This class has no
 * dependency on {@code FriendshipRepository} or
 * {@code com.imin.backend.social.SocialService} at all — direct messaging
 * is never conditioned on either party having added the other as a friend,
 * per spec.md's explicit correction (Resolved Questions item 2: "Direct
 * messaging is not gated by friend status in any way... an earlier version
 * of this spec incorrectly required a friend-add as a precondition for
 * messaging; that requirement has been retracted"). Any registered user may
 * message any other registered user.
 *
 * <p><strong>Blocking gates sending only, never viewing.</strong>
 * {@link #sendMessage} checks, at send time (every time — not cached, since
 * a block can be applied between polls), whether the recipient has blocked
 * the sender via {@link BlockRepository#existsByBlockerIdAndBlockedId}; if
 * so, sending is rejected with 403. {@link #getMessages} performs no block
 * check at all — a thread's two participants can always read their own
 * conversation history regardless of either party's block status, since the
 * spec's blocking rule is about preventing the blocked person from
 * <em>writing</em>, not about retroactively hiding a participant's own
 * message history (spec.md Direct chats: "the only restriction on sending a
 * direct message is blocking" — reading is never mentioned as restricted).
 */
@Service
@RequiredArgsConstructor
public class DirectChatService {

    /** Page size for the initial (no-cursor) poll — mirrors {@code GroupChatService}. */
    static final int INITIAL_PAGE_SIZE = 50;

    private final DirectThreadRepository threadRepository;
    private final DirectMessageRepository messageRepository;
    private final BlockRepository blockRepository;
    private final UserRepository userRepository;

    /**
     * Send {@code callerEmail} a message to {@code targetUserId}. Creates the
     * thread on first contact if it doesn't already exist (lazy
     * creation — design.md §4's "get-or-create-on-read"/"thread on first
     * message" pattern). The only check performed is whether the recipient
     * has blocked the sender; there is no friend-add precondition of any
     * kind, in either direction.
     */
    @Transactional
    public DirectMessageResponse sendMessage(String callerEmail, Long targetUserId, PostDirectMessageRequest request) {
        User caller = findUserByEmail(callerEmail);
        requireTargetExists(targetUserId);
        requireNotSelf(caller.getId(), targetUserId);

        if (blockRepository.existsByBlockerIdAndBlockedId(targetUserId, caller.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This user has blocked you");
        }

        DirectThread thread = findOrCreateThread(caller.getId(), targetUserId);

        DirectMessage message = new DirectMessage();
        message.setThreadId(thread.getId());
        message.setSenderId(caller.getId());
        message.setBody(request.body());
        messageRepository.save(message);

        return DirectMessageResponse.from(message, caller);
    }

    /**
     * Polling read of the thread between the caller and {@code targetUserId}.
     * {@code after == null} returns the most recent {@link #INITIAL_PAGE_SIZE}
     * messages (chronological order); {@code after != null} returns every
     * message with {@code id > after}, ascending. No thread existing yet
     * (i.e. the pair has never exchanged a message) is not an error — it
     * simply returns an empty list, since a thread is only lazily created on
     * first send.
     *
     * <p>No block check is performed here, in either direction — see class
     * javadoc. A participant can always read their own conversation history.
     */
    public List<DirectMessageResponse> getMessages(String callerEmail, Long targetUserId, Long after) {
        User caller = findUserByEmail(callerEmail);
        requireTargetExists(targetUserId);
        requireNotSelf(caller.getId(), targetUserId);

        Optional<DirectThread> existingThread = findThread(caller.getId(), targetUserId);
        if (existingThread.isEmpty()) {
            return List.of();
        }
        Long threadId = existingThread.get().getId();

        List<DirectMessage> messages;
        if (after == null) {
            messages = messageRepository.findByThreadIdOrderByIdDesc(threadId, PageRequest.of(0, INITIAL_PAGE_SIZE));
            messages = messages.stream()
                    .sorted(Comparator.comparing(DirectMessage::getId))
                    .toList();
        } else {
            messages = messageRepository.findByThreadIdAndIdGreaterThanOrderByIdAsc(threadId, after);
        }

        Map<Long, User> usersById = userRepository.findAllById(
                messages.stream().map(DirectMessage::getSenderId).distinct().toList()
        ).stream().collect(Collectors.toMap(User::getId, u -> u));

        return messages.stream()
                .map(m -> DirectMessageResponse.from(m, usersById.get(m.getSenderId())))
                .toList();
    }

    /**
     * List the caller's DM threads, each with the other participant's info
     * and a last-message preview (kept simple per the task's "keep it
     * simple for MVP" steer).
     */
    public List<DirectThreadResponse> listThreads(String callerEmail) {
        User caller = findUserByEmail(callerEmail);
        List<DirectThread> threads = threadRepository.findByUserAIdOrUserBId(caller.getId(), caller.getId());

        List<Long> otherUserIds = threads.stream()
                .map(t -> otherUserId(t, caller.getId()))
                .toList();
        Map<Long, User> usersById = userRepository.findAllById(otherUserIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        return threads.stream()
                .map(t -> {
                    User other = usersById.get(otherUserId(t, caller.getId()));
                    var lastMessage = messageRepository.findFirstByThreadIdOrderByIdDesc(t.getId());
                    String lastBody = lastMessage.map(DirectMessage::getBody).orElse(null);
                    var lastAt = lastMessage.map(DirectMessage::getCreatedAt).orElse(null);
                    return DirectThreadResponse.from(t, other, lastBody, lastAt);
                })
                .toList();
    }

    /**
     * Lookup-or-create, normalizing the pair so {@code userAId < userBId}
     * (see {@link DirectThread} javadoc) — this is what structurally
     * guarantees at most one thread per unordered pair regardless of who
     * messages first.
     */
    private DirectThread findOrCreateThread(Long userId1, Long userId2) {
        long lower = Math.min(userId1, userId2);
        long upper = Math.max(userId1, userId2);
        return threadRepository.findByUserAIdAndUserBId(lower, upper)
                .orElseGet(() -> {
                    DirectThread thread = new DirectThread();
                    thread.setUserAId(lower);
                    thread.setUserBId(upper);
                    return threadRepository.save(thread);
                });
    }

    private Optional<DirectThread> findThread(Long userId1, Long userId2) {
        long lower = Math.min(userId1, userId2);
        long upper = Math.max(userId1, userId2);
        return threadRepository.findByUserAIdAndUserBId(lower, upper);
    }

    private Long otherUserId(DirectThread thread, Long callerId) {
        return thread.getUserAId().equals(callerId) ? thread.getUserBId() : thread.getUserAId();
    }

    private void requireNotSelf(Long callerId, Long targetUserId) {
        if (callerId.equals(targetUserId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You cannot direct-message yourself");
        }
    }

    private void requireTargetExists(Long targetUserId) {
        if (!userRepository.existsById(targetUserId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }
    }

    private User findUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }
}
