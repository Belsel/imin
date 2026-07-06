package com.imin.backend.social;

import com.imin.backend.social.dto.BlockResponse;
import com.imin.backend.social.dto.FriendResponse;
import com.imin.backend.user.User;
import com.imin.backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Owns friend-add/unfriend and block/unblock — both one-directional edges,
 * each a standalone relationship in MVP (see spec.md Users + Resolved
 * Questions items 1–3). Neither relation gates anything else: in
 * particular, this service has no dependency on
 * {@link com.imin.backend.chat.DirectChatService} or vice versa beyond that
 * service's own direct {@link BlockRepository} lookup at send-time — friend
 * status is never consulted for messaging, by construction (there is no
 * {@link FriendshipRepository} reference anywhere in the chat package).
 */
@Service
@RequiredArgsConstructor
public class SocialService {

    private final FriendshipRepository friendshipRepository;
    private final BlockRepository blockRepository;
    private final UserRepository userRepository;

    /**
     * Add {@code targetUserId} as a friend of the caller. Idempotent: adding
     * an already-added friend is a no-op success (mirrors
     * {@code GroupService#joinGroup}'s "re-joining is a no-op" convention)
     * rather than a 409 — a friend-add has no meaningful "duplicate attempt"
     * failure mode worth surfacing to the caller.
     */
    @Transactional
    public void addFriend(String callerEmail, Long targetUserId) {
        User caller = findUserByEmail(callerEmail);
        requireTargetExists(targetUserId);
        requireNotSelf(caller.getId(), targetUserId, "You cannot add yourself as a friend");

        if (!friendshipRepository.existsByFollowerIdAndFolloweeId(caller.getId(), targetUserId)) {
            Friendship friendship = new Friendship();
            friendship.setFollowerId(caller.getId());
            friendship.setFolloweeId(targetUserId);
            friendshipRepository.save(friendship);
        }
    }

    /**
     * Remove the caller's own friend-add of {@code targetUserId}. Idempotent:
     * removing a friendship that doesn't exist is a no-op success, not an
     * error — consistent with the spec's "no confirmation step" framing for
     * unfriending and standard DELETE-is-idempotent REST semantics.
     */
    @Transactional
    public void removeFriend(String callerEmail, Long targetUserId) {
        User caller = findUserByEmail(callerEmail);
        friendshipRepository.findByFollowerIdAndFolloweeId(caller.getId(), targetUserId)
                .ifPresent(friendshipRepository::delete);
    }

    public List<FriendResponse> listFriends(String callerEmail) {
        User caller = findUserByEmail(callerEmail);
        List<Friendship> friendships = friendshipRepository.findByFollowerId(caller.getId());
        Map<Long, User> usersById = usersById(friendships.stream().map(Friendship::getFolloweeId).toList());
        return friendships.stream()
                .map(f -> FriendResponse.from(f, usersById.get(f.getFolloweeId())))
                .toList();
    }

    /**
     * Block {@code targetUserId}. Idempotent: blocking an already-blocked
     * user is a no-op success, consistent with the add-friend convention
     * above.
     */
    @Transactional
    public void blockUser(String callerEmail, Long targetUserId) {
        User caller = findUserByEmail(callerEmail);
        requireTargetExists(targetUserId);
        requireNotSelf(caller.getId(), targetUserId, "You cannot block yourself");

        if (!blockRepository.existsByBlockerIdAndBlockedId(caller.getId(), targetUserId)) {
            Block block = new Block();
            block.setBlockerId(caller.getId());
            block.setBlockedId(targetUserId);
            blockRepository.save(block);
        }
    }

    /**
     * Unblock {@code targetUserId}. Idempotent: unblocking a user who isn't
     * blocked is a no-op success, not an error — same rationale as
     * {@link #removeFriend}.
     */
    @Transactional
    public void unblockUser(String callerEmail, Long targetUserId) {
        User caller = findUserByEmail(callerEmail);
        blockRepository.findByBlockerIdAndBlockedId(caller.getId(), targetUserId)
                .ifPresent(blockRepository::delete);
    }

    public List<BlockResponse> listBlocks(String callerEmail) {
        User caller = findUserByEmail(callerEmail);
        List<Block> blocks = blockRepository.findByBlockerId(caller.getId());
        Map<Long, User> usersById = usersById(blocks.stream().map(Block::getBlockedId).toList());
        return blocks.stream()
                .map(b -> BlockResponse.from(b, usersById.get(b.getBlockedId())))
                .toList();
    }

    private void requireNotSelf(Long callerId, Long targetUserId, String message) {
        if (callerId.equals(targetUserId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
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

    private Map<Long, User> usersById(List<Long> ids) {
        return userRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
    }
}
