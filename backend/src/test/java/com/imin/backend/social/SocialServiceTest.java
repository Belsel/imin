package com.imin.backend.social;

import com.imin.backend.social.dto.BlockResponse;
import com.imin.backend.social.dto.FriendResponse;
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
 * Implementer sanity checks for {@link SocialService} — not a substitute
 * for the tester stage's full acceptance-criteria suite. Targets the
 * one-directional, no-reciprocity nature of friends/blocks, and the
 * idempotency conventions for add/remove and block/unblock.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SocialServiceTest {

    @Autowired
    private SocialService socialService;
    @Autowired
    private FriendshipRepository friendshipRepository;
    @Autowired
    private BlockRepository blockRepository;
    @Autowired
    private UserRepository userRepository;

    private User alice;
    private User bob;

    @BeforeEach
    void setUp() {
        friendshipRepository.deleteAll();
        blockRepository.deleteAll();
        userRepository.deleteAll();

        alice = createUser("alice@example.com", "Alice");
        bob = createUser("bob@example.com", "Bob");
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
    void addingFriendIsImmediateAndOneDirectional() {
        socialService.addFriend(alice.getEmail(), bob.getId());

        List<FriendResponse> aliceFriends = socialService.listFriends(alice.getEmail());
        assertThat(aliceFriends).extracting(FriendResponse::userId).containsExactly(bob.getId());

        // Bob has not thereby added Alice -- each direction is independent.
        List<FriendResponse> bobFriends = socialService.listFriends(bob.getEmail());
        assertThat(bobFriends).isEmpty();
    }

    @Test
    void addingAnAlreadyAddedFriendIsANoOpNotAnError() {
        socialService.addFriend(alice.getEmail(), bob.getId());
        socialService.addFriend(alice.getEmail(), bob.getId());

        assertThat(socialService.listFriends(alice.getEmail())).hasSize(1);
    }

    @Test
    void unfriendingRemovesOnlyTheCallersOwnAddAndTakesEffectImmediately() {
        socialService.addFriend(alice.getEmail(), bob.getId());
        socialService.addFriend(bob.getEmail(), alice.getId());

        socialService.removeFriend(alice.getEmail(), bob.getId());

        assertThat(socialService.listFriends(alice.getEmail())).isEmpty();
        // Bob's separate add of Alice is untouched.
        assertThat(socialService.listFriends(bob.getEmail()))
                .extracting(FriendResponse::userId).containsExactly(alice.getId());
    }

    @Test
    void removingANonExistentFriendshipIsANoOpNotAnError() {
        socialService.removeFriend(alice.getEmail(), bob.getId());
        assertThat(socialService.listFriends(alice.getEmail())).isEmpty();
    }

    @Test
    void cannotAddSelfAsFriend() {
        assertThatThrownBy(() -> socialService.addFriend(alice.getEmail(), alice.getId()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(400));
    }

    @Test
    void blockingIsOneDirectionalAndDoesNotTouchFriendshipRecords() {
        socialService.addFriend(alice.getEmail(), bob.getId());

        socialService.blockUser(bob.getEmail(), alice.getId());

        List<BlockResponse> bobBlocks = socialService.listBlocks(bob.getEmail());
        assertThat(bobBlocks).extracting(BlockResponse::userId).containsExactly(alice.getId());
        // Alice has not thereby blocked Bob.
        assertThat(socialService.listBlocks(alice.getEmail())).isEmpty();

        // The block does not delete or otherwise affect Alice's friend-add of Bob.
        assertThat(socialService.listFriends(alice.getEmail()))
                .extracting(FriendResponse::userId).containsExactly(bob.getId());
    }

    @Test
    void blockingAnAlreadyBlockedUserIsANoOpNotAnError() {
        socialService.blockUser(alice.getEmail(), bob.getId());
        socialService.blockUser(alice.getEmail(), bob.getId());

        assertThat(socialService.listBlocks(alice.getEmail())).hasSize(1);
    }

    @Test
    void unblockingRemovesTheBlockAndUnblockingANonBlockedUserIsANoOp() {
        socialService.blockUser(alice.getEmail(), bob.getId());
        socialService.unblockUser(alice.getEmail(), bob.getId());
        assertThat(socialService.listBlocks(alice.getEmail())).isEmpty();

        // Unblocking again (already unblocked) is a no-op, not an error.
        socialService.unblockUser(alice.getEmail(), bob.getId());
        assertThat(socialService.listBlocks(alice.getEmail())).isEmpty();
    }

    @Test
    void cannotBlockSelf() {
        assertThatThrownBy(() -> socialService.blockUser(alice.getEmail(), alice.getId()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(400));
    }

    // ---- try-demo-account restriction enforcement (specs/try-demo-account/spec.md) ----

    @Test
    void demoAccountCanStillAddFriendBlockAndUnblockNormally() {
        // Spot-check of SocialService's explicitly-allowed surface for the
        // demo account -- unchanged from a normal user, no 403 anywhere here.
        User demo = createUser("demo@example.com", "Demo User");
        demo.setDemoAccount(true);
        userRepository.save(demo);

        socialService.addFriend(demo.getEmail(), bob.getId());
        assertThat(socialService.listFriends(demo.getEmail())).extracting(FriendResponse::userId).containsExactly(bob.getId());

        socialService.blockUser(demo.getEmail(), alice.getId());
        assertThat(socialService.listBlocks(demo.getEmail())).extracting(BlockResponse::userId).containsExactly(alice.getId());

        socialService.unblockUser(demo.getEmail(), alice.getId());
        assertThat(socialService.listBlocks(demo.getEmail())).isEmpty();

        socialService.removeFriend(demo.getEmail(), bob.getId());
        assertThat(socialService.listFriends(demo.getEmail())).isEmpty();
    }

    @Test
    void anotherUserCanStillBlockTheDemoAccountAsATarget() {
        // Security edge case: blocking works normally when the demo account
        // is the *target* -- the demo-actor-only block never widens/narrows
        // what another user can do to the demo account.
        User demo = createUser("demo@example.com", "Demo User");
        demo.setDemoAccount(true);
        userRepository.save(demo);

        socialService.blockUser(alice.getEmail(), demo.getId());

        assertThat(socialService.listBlocks(alice.getEmail())).extracting(BlockResponse::userId).containsExactly(demo.getId());
    }
}
