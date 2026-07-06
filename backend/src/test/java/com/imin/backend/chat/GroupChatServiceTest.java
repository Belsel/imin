package com.imin.backend.chat;

import com.imin.backend.chat.dto.GroupChatMessageResponse;
import com.imin.backend.chat.dto.PostGroupChatMessageRequest;
import com.imin.backend.group.GroupBanRepository;
import com.imin.backend.group.GroupMembershipRepository;
import com.imin.backend.group.GroupRepository;
import com.imin.backend.group.GroupService;
import com.imin.backend.group.dto.CreateGroupRequest;
import com.imin.backend.group.dto.GroupResponse;
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
 * Implementer sanity checks for {@link GroupChatService} — not a substitute
 * for the tester stage's full acceptance-criteria suite. Exercises the
 * membership/ban-gated authorization (reusing Slice 2's {@code GroupService}
 * directly to set up membership/ban state) and the id-cursor polling
 * contract.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class GroupChatServiceTest {

    @Autowired
    private GroupChatService groupChatService;
    @Autowired
    private GroupChatMessageRepository messageRepository;
    @Autowired
    private GroupService groupService;
    @Autowired
    private GroupRepository groupRepository;
    @Autowired
    private GroupMembershipRepository membershipRepository;
    @Autowired
    private GroupBanRepository banRepository;
    @Autowired
    private UserRepository userRepository;

    private User alice;
    private User bob;

    @BeforeEach
    void setUp() {
        messageRepository.deleteAll();
        banRepository.deleteAll();
        membershipRepository.deleteAll();
        groupRepository.deleteAll();
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

    private GroupResponse createGroup(User creator, String name) {
        return groupService.createGroup(creator.getEmail(), new CreateGroupRequest(name, "desc", 1.0, 1.0, List.of()));
    }

    @Test
    void memberCanPostAndReadMessages() {
        GroupResponse group = createGroup(alice, "Chatters");
        groupService.joinGroup(bob.getEmail(), group.id());

        groupChatService.postMessage(alice.getEmail(), group.id(), new PostGroupChatMessageRequest("hello"));
        groupChatService.postMessage(bob.getEmail(), group.id(), new PostGroupChatMessageRequest("hi alice"));

        List<GroupChatMessageResponse> messages = groupChatService.getMessages(alice.getEmail(), group.id(), null);
        assertThat(messages).hasSize(2);
        assertThat(messages.get(0).body()).isEqualTo("hello");
        assertThat(messages.get(1).body()).isEqualTo("hi alice");
        assertThat(messages.get(1).senderDisplayName()).isEqualTo("Bob");
    }

    @Test
    void pollingAfterCursorReturnsOnlyNewerMessagesInChronologicalOrder() {
        GroupResponse group = createGroup(alice, "Pollers");

        GroupChatMessageResponse first = groupChatService.postMessage(alice.getEmail(), group.id(),
                new PostGroupChatMessageRequest("first"));
        groupChatService.postMessage(alice.getEmail(), group.id(), new PostGroupChatMessageRequest("second"));
        groupChatService.postMessage(alice.getEmail(), group.id(), new PostGroupChatMessageRequest("third"));

        List<GroupChatMessageResponse> afterFirst = groupChatService.getMessages(alice.getEmail(), group.id(), first.id());

        assertThat(afterFirst).hasSize(2);
        assertThat(afterFirst.get(0).body()).isEqualTo("second");
        assertThat(afterFirst.get(1).body()).isEqualTo("third");
    }

    @Test
    void nonMemberCannotViewOrPostToGroupChat() {
        GroupResponse group = createGroup(alice, "Private Group");

        assertThatThrownBy(() -> groupChatService.getMessages(bob.getEmail(), group.id(), null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(403));

        assertThatThrownBy(() -> groupChatService.postMessage(bob.getEmail(), group.id(), new PostGroupChatMessageRequest("hi")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(403));
    }

    @Test
    void bannedMemberLosesChatAccessImmediatelyAndRegainsItOnUnban() {
        GroupResponse group = createGroup(alice, "Banhammer Group");
        groupService.joinGroup(bob.getEmail(), group.id());
        groupChatService.postMessage(bob.getEmail(), group.id(), new PostGroupChatMessageRequest("before ban"));

        groupService.banMember(alice.getEmail(), group.id(), bob.getId());

        assertThatThrownBy(() -> groupChatService.getMessages(bob.getEmail(), group.id(), null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(403));
        assertThatThrownBy(() -> groupChatService.postMessage(bob.getEmail(), group.id(), new PostGroupChatMessageRequest("during ban")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(403));

        groupService.unbanMember(alice.getEmail(), group.id(), bob.getId());
        groupService.joinGroup(bob.getEmail(), group.id());

        List<GroupChatMessageResponse> messages = groupChatService.getMessages(bob.getEmail(), group.id(), null);
        assertThat(messages).extracting(GroupChatMessageResponse::body).contains("before ban");
    }

    @Test
    void unbannedMemberCanPostAgainAndIsVisibleToOtherMembersOnNextPoll() {
        // Strengthens bannedMemberLosesChatAccessImmediatelyAndRegainsItOnUnban,
        // which only re-confirmed *read* access to pre-ban history after
        // unban/rejoin. This test confirms the full round-trip the spec's
        // "regains visibility immediately upon unban" wording implies: the
        // formerly-banned member can POST again, and that new message is
        // visible to *another* member (alice) polling after the fact -- not
        // just visible to bob himself.
        GroupResponse group = createGroup(alice, "Round Trip Group");
        groupService.joinGroup(bob.getEmail(), group.id());
        GroupChatMessageResponse beforeBan = groupChatService.postMessage(bob.getEmail(), group.id(),
                new PostGroupChatMessageRequest("before ban"));

        groupService.banMember(alice.getEmail(), group.id(), bob.getId());
        assertThatThrownBy(() -> groupChatService.postMessage(bob.getEmail(), group.id(), new PostGroupChatMessageRequest("during ban")))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(403));

        groupService.unbanMember(alice.getEmail(), group.id(), bob.getId());
        groupService.joinGroup(bob.getEmail(), group.id());

        GroupChatMessageResponse afterUnban = groupChatService.postMessage(bob.getEmail(), group.id(),
                new PostGroupChatMessageRequest("after unban"));

        // alice (never banned, polling from before bob's post-unban message)
        // sees bob's new message immediately -- visibility is restored for
        // both directions (bob reading, and others reading bob), not just
        // bob regaining his own read access.
        List<GroupChatMessageResponse> aliceView = groupChatService.getMessages(alice.getEmail(), group.id(), beforeBan.id());
        assertThat(aliceView).extracting(GroupChatMessageResponse::body).contains("after unban");

        // bob can also read the full history again, including his own
        // pre-ban and post-unban messages.
        List<GroupChatMessageResponse> bobView = groupChatService.getMessages(bob.getEmail(), group.id(), null);
        assertThat(bobView).extracting(GroupChatMessageResponse::body)
                .contains("before ban", "after unban");
        assertThat(afterUnban.body()).isEqualTo("after unban");
    }

    @Test
    void eachGroupHasIndependentChatMessagesAreNeverVisibleAcrossGroups() {
        // "Each group has exactly one associated chat" implies two distinct
        // groups have fully independent message streams: a message posted to
        // Group A must never appear when polling Group B, even for a user who
        // is a current member of both groups simultaneously.
        GroupResponse groupA = createGroup(alice, "Group A");
        GroupResponse groupB = createGroup(alice, "Group B");
        groupService.joinGroup(bob.getEmail(), groupA.id());
        groupService.joinGroup(bob.getEmail(), groupB.id());

        groupChatService.postMessage(alice.getEmail(), groupA.id(), new PostGroupChatMessageRequest("only in A"));
        groupChatService.postMessage(bob.getEmail(), groupB.id(), new PostGroupChatMessageRequest("only in B"));

        List<GroupChatMessageResponse> messagesInA = groupChatService.getMessages(bob.getEmail(), groupA.id(), null);
        List<GroupChatMessageResponse> messagesInB = groupChatService.getMessages(bob.getEmail(), groupB.id(), null);

        assertThat(messagesInA).extracting(GroupChatMessageResponse::body).containsExactly("only in A");
        assertThat(messagesInB).extracting(GroupChatMessageResponse::body).containsExactly("only in B");

        // Polling B's stream by id never surfaces A's message, even though
        // both groups' message ids share the same underlying sequence/table.
        assertThat(messagesInB).extracting(GroupChatMessageResponse::body).doesNotContain("only in A");
        assertThat(messagesInA).extracting(GroupChatMessageResponse::body).doesNotContain("only in B");
    }

    @Test
    void groupNotFoundReturns404() {
        assertThatThrownBy(() -> groupChatService.getMessages(alice.getEmail(), 999_999L, null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode().value()).isEqualTo(404));
    }
}
