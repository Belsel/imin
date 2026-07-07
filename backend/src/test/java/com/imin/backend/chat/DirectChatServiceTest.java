package com.imin.backend.chat;

import com.imin.backend.chat.dto.DirectMessageResponse;
import com.imin.backend.chat.dto.DirectThreadResponse;
import com.imin.backend.chat.dto.PostDirectMessageRequest;
import com.imin.backend.social.Block;
import com.imin.backend.social.BlockRepository;
import com.imin.backend.social.FriendshipRepository;
import com.imin.backend.social.SocialService;
import com.imin.backend.user.AuthProvider;
import com.imin.backend.user.User;
import com.imin.backend.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Implementer sanity checks for {@link DirectChatService} — not a
 * substitute for the tester stage's full acceptance-criteria suite.
 * Specifically exercises the most important, multi-round-corrected rule in
 * this slice: direct messaging has <strong>no friend-gating of any
 * kind</strong>, and is gated <strong>only</strong> by the recipient
 * blocking the sender (which gates sending, not viewing).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DirectChatServiceTest {

    @Autowired
    private DirectChatService directChatService;
    @Autowired
    private SocialService socialService;
    @Autowired
    private DirectThreadRepository threadRepository;
    @Autowired
    private DirectMessageRepository messageRepository;
    @Autowired
    private BlockRepository blockRepository;
    @Autowired
    private FriendshipRepository friendshipRepository;
    @Autowired
    private UserRepository userRepository;

    private User alice;
    private User bob;

    @BeforeEach
    void setUp() {
        messageRepository.deleteAll();
        threadRepository.deleteAll();
        blockRepository.deleteAll();
        friendshipRepository.deleteAll();
        userRepository.deleteAll();

        alice = createUser("dm-alice@example.com", "Alice");
        bob = createUser("dm-bob@example.com", "Bob");
    }

    private User createUser(String email, String displayName) {
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash("hash");
        user.setDisplayName(displayName);
        user.setProvider(AuthProvider.LOCAL);
        user.setEmailVerified(true);
        return userRepository.save(user);
    }

    @Test
    void userCanMessageAnyOtherUserWithNoFriendAddInEitherDirection() {
        // Neither alice nor bob has added the other as a friend at all.
        assertThat(friendshipRepository.findByFollowerId(alice.getId())).isEmpty();
        assertThat(friendshipRepository.findByFollowerId(bob.getId())).isEmpty();

        DirectMessageResponse sent = directChatService.sendMessage(
                alice.getEmail(), bob.getId(), new PostDirectMessageRequest("hi bob, no friend-add needed"));

        assertThat(sent.body()).isEqualTo("hi bob, no friend-add needed");
        assertThat(sent.senderId()).isEqualTo(alice.getId());
    }

    @Test
    void sendingDoesNotConsultFriendshipRepositoryRegardlessOfFriendStateInEitherDirection() {
        // Even with friend-adds existing in BOTH directions, sending must
        // behave identically to having no friend-adds at all -- friend
        // status must never be read for this decision.
        socialService.addFriend(alice.getEmail(), bob.getId());
        socialService.addFriend(bob.getEmail(), alice.getId());

        DirectMessageResponse aliceToBob = directChatService.sendMessage(
                alice.getEmail(), bob.getId(), new PostDirectMessageRequest("with mutual friend-adds"));
        DirectMessageResponse bobToAlice = directChatService.sendMessage(
                bob.getEmail(), alice.getId(), new PostDirectMessageRequest("reply"));

        assertThat(aliceToBob.body()).isEqualTo("with mutual friend-adds");
        assertThat(bobToAlice.body()).isEqualTo("reply");
    }

    @Test
    void userCanMessageSomeoneWhoHasNotAddedThemAndWhomTheyHaveNotAdded() {
        // A has not added B, B has not added A -- still allowed.
        DirectMessageResponse sent = directChatService.sendMessage(
                alice.getEmail(), bob.getId(), new PostDirectMessageRequest("still allowed"));
        assertThat(sent).isNotNull();
    }

    @Test
    void blockedSenderCannotMessageTheBlockerEvenWithNoFriendHistory() {
        socialService.blockUser(bob.getEmail(), alice.getId());

        assertThatThrownBy(() -> directChatService.sendMessage(
                alice.getEmail(), bob.getId(), new PostDirectMessageRequest("blocked")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(403));
    }

    @Test
    void blockOverridesAPriorFriendAddFriendAddHasNoBearingOnTheBlock() {
        // Alice previously added Bob as a friend, then Bob blocks Alice --
        // the prior friend-add must not exempt Alice from the block.
        socialService.addFriend(alice.getEmail(), bob.getId());
        socialService.blockUser(bob.getEmail(), alice.getId());

        assertThatThrownBy(() -> directChatService.sendMessage(
                alice.getEmail(), bob.getId(), new PostDirectMessageRequest("still blocked")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(403));

        // The friend-add record itself persists unchanged underneath the block.
        assertThat(socialService.listFriends(alice.getEmail()))
                .extracting(friend -> friend.userId()).containsExactly(bob.getId());
    }

    @Test
    void blockOnlyGatesTheBlockedDirectionTheBlockerCanStillMessageTheBlockedUser() {
        socialService.blockUser(bob.getEmail(), alice.getId());

        // Bob (the blocker) can still message Alice -- the block only stops
        // alice -> bob, not bob -> alice.
        DirectMessageResponse sent = directChatService.sendMessage(
                bob.getEmail(), alice.getId(), new PostDirectMessageRequest("bob can still write"));
        assertThat(sent.body()).isEqualTo("bob can still write");
    }

    @Test
    void viewingMessageHistoryIsUnaffectedByBlockOnlySendingIsGated() {
        // Exchange messages while unblocked.
        directChatService.sendMessage(alice.getEmail(), bob.getId(), new PostDirectMessageRequest("before block 1"));
        directChatService.sendMessage(bob.getEmail(), alice.getId(), new PostDirectMessageRequest("before block 2"));

        // Bob blocks Alice -- this gates Alice's future sends only.
        socialService.blockUser(bob.getEmail(), alice.getId());
        assertThatThrownBy(() -> directChatService.sendMessage(
                alice.getEmail(), bob.getId(), new PostDirectMessageRequest("cannot send now")))
                .isInstanceOf(ResponseStatusException.class);

        // Both participants can still read the full existing history --
        // viewing is never gated by block status, for either party.
        List<DirectMessageResponse> aliceView = directChatService.getMessages(alice.getEmail(), bob.getId(), null);
        List<DirectMessageResponse> bobView = directChatService.getMessages(bob.getEmail(), alice.getId(), null);

        assertThat(aliceView).extracting(DirectMessageResponse::body)
                .containsExactly("before block 1", "before block 2");
        assertThat(bobView).extracting(DirectMessageResponse::body)
                .containsExactly("before block 1", "before block 2");
    }

    @Test
    void atMostOneThreadExistsPerUnorderedPairRegardlessOfWhoMessagesFirst() {
        directChatService.sendMessage(alice.getEmail(), bob.getId(), new PostDirectMessageRequest("alice first"));
        directChatService.sendMessage(bob.getEmail(), alice.getId(), new PostDirectMessageRequest("bob replies"));

        long lower = Math.min(alice.getId(), bob.getId());
        long upper = Math.max(alice.getId(), bob.getId());
        assertThat(threadRepository.findByUserAIdAndUserBId(lower, upper)).isPresent();

        // Polling from either direction returns the same shared conversation.
        List<DirectMessageResponse> fromAlice = directChatService.getMessages(alice.getEmail(), bob.getId(), null);
        List<DirectMessageResponse> fromBob = directChatService.getMessages(bob.getEmail(), alice.getId(), null);
        assertThat(fromAlice).extracting(DirectMessageResponse::body)
                .containsExactly("alice first", "bob replies");
        assertThat(fromBob).extracting(DirectMessageResponse::body)
                .containsExactly("alice first", "bob replies");
    }

    @Test
    void pollingAfterCursorReturnsOnlyNewerMessages() {
        DirectMessageResponse first = directChatService.sendMessage(
                alice.getEmail(), bob.getId(), new PostDirectMessageRequest("first"));
        directChatService.sendMessage(alice.getEmail(), bob.getId(), new PostDirectMessageRequest("second"));

        List<DirectMessageResponse> afterFirst = directChatService.getMessages(bob.getEmail(), alice.getId(), first.id());
        assertThat(afterFirst).extracting(DirectMessageResponse::body).containsExactly("second");
    }

    @Test
    void viewingANonExistentThreadReturnsEmptyNotAnError() {
        List<DirectMessageResponse> messages = directChatService.getMessages(alice.getEmail(), bob.getId(), null);
        assertThat(messages).isEmpty();
    }

    @Test
    void listThreadsShowsOtherParticipantAndLastMessagePreview() {
        directChatService.sendMessage(alice.getEmail(), bob.getId(), new PostDirectMessageRequest("hello"));
        directChatService.sendMessage(bob.getEmail(), alice.getId(), new PostDirectMessageRequest("hey back"));

        List<DirectThreadResponse> aliceThreads = directChatService.listThreads(alice.getEmail());
        assertThat(aliceThreads).hasSize(1);
        assertThat(aliceThreads.get(0).otherUserId()).isEqualTo(bob.getId());
        assertThat(aliceThreads.get(0).lastMessageBody()).isEqualTo("hey back");

        List<DirectThreadResponse> bobThreads = directChatService.listThreads(bob.getEmail());
        assertThat(bobThreads).hasSize(1);
        assertThat(bobThreads.get(0).otherUserId()).isEqualTo(alice.getId());
    }

    @Test
    void cannotDirectMessageSelf() {
        assertThatThrownBy(() -> directChatService.sendMessage(
                alice.getEmail(), alice.getId(), new PostDirectMessageRequest("talking to myself")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(400));
    }

    @Test
    void blockDoesNotDeleteOrTouchAnyFriendshipRow() {
        socialService.addFriend(bob.getEmail(), alice.getId());
        Block block = new Block();
        block.setBlockerId(bob.getId());
        block.setBlockedId(alice.getId());
        blockRepository.save(block);

        assertThat(friendshipRepository.existsByFollowerIdAndFolloweeId(bob.getId(), alice.getId())).isTrue();
    }

    // ---- try-demo-account restriction enforcement (specs/try-demo-account/spec.md) ----

    @Test
    void demoAccountCanStillSendAndReadDirectMessagesNormally() {
        // Spot-check of DirectChatService's explicitly-allowed surface
        // (sendMessage, getMessages, listThreads) for the demo account.
        User demo = createUser("dm-demo@example.com", "Demo User");
        demo.setDemoAccount(true);
        userRepository.save(demo);

        DirectMessageResponse sent = directChatService.sendMessage(
                demo.getEmail(), alice.getId(), new PostDirectMessageRequest("hi from demo"));
        assertThat(sent.body()).isEqualTo("hi from demo");

        assertThat(directChatService.getMessages(demo.getEmail(), alice.getId(), null))
                .extracting(DirectMessageResponse::body).contains("hi from demo");
        assertThat(directChatService.listThreads(demo.getEmail()))
                .extracting(DirectThreadResponse::otherUserId).contains(alice.getId());
    }

    @Test
    void anotherUserBlockingTheDemoAccountStillPreventsItFromDmingThem() {
        // Security edge case: blocking still works normally when the demo
        // account is the target -- the demo-actor-only restriction never
        // exempts the demo account from being blocked like any other user.
        User demo = createUser("dm-demo-blocked@example.com", "Demo User");
        demo.setDemoAccount(true);
        userRepository.save(demo);

        socialService.blockUser(alice.getEmail(), demo.getId());

        assertThatThrownBy(() -> directChatService.sendMessage(
                demo.getEmail(), alice.getId(), new PostDirectMessageRequest("can't reach alice")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(403));
    }
}
