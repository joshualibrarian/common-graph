package dev.everydaythings.graph.policy;

import dev.everydaythings.graph.item.Item;
import dev.everydaythings.graph.item.component.Roster;
import dev.everydaythings.graph.item.id.ItemID;
import dev.everydaythings.graph.policy.PolicySet.AccessPolicy;
import dev.everydaythings.graph.policy.PolicySet.AccessPolicy.*;
import dev.everydaythings.graph.runtime.Librarian;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("slow")
class ItemPolicyResolverTest {

    private Librarian lib;
    private Item item;
    private ItemID ownerId;
    private ItemID memberId;
    private ItemID strangerId;
    private ItemID hostId;

    @BeforeEach
    void setUp() {
        lib = Librarian.createInMemory();
        item = Item.create(lib);

        ownerId = ItemID.random();
        memberId = ItemID.random();
        strangerId = ItemID.random();
        hostId = ItemID.random();
    }

    // ==================================================================================
    // isOwner
    // ==================================================================================

    @Nested
    class IsOwner {

        @Test
        void matchesAuthorityPolicyOwner() {
            setOwner(ownerId);
            var resolver = new ItemPolicyResolver(item, hostId);

            assertThat(resolver.isOwner(item.iid(), ownerId)).isTrue();
        }

        @Test
        void rejectsNonOwner() {
            setOwner(ownerId);
            var resolver = new ItemPolicyResolver(item, hostId);

            assertThat(resolver.isOwner(item.iid(), strangerId)).isFalse();
        }

        @Test
        void falseWhenNoAuthorityPolicy() {
            var resolver = new ItemPolicyResolver(item, hostId);

            assertThat(resolver.isOwner(item.iid(), ownerId)).isFalse();
        }

        @Test
        void falseWhenNullSubject() {
            setOwner(ownerId);
            var resolver = new ItemPolicyResolver(item, hostId);

            assertThat(resolver.isOwner(item.iid(), null)).isFalse();
        }

        @Test
        void falseWhenOwnerIdNull() {
            item.policy().authority(AuthorityPolicy.builder().build());
            var resolver = new ItemPolicyResolver(item, hostId);

            assertThat(resolver.isOwner(item.iid(), ownerId)).isFalse();
        }
    }

    // ==================================================================================
    // isParticipant
    // ==================================================================================

    @Nested
    class IsParticipant {

        @Test
        void matchesRosterMember() {
            addRosterWithMember(memberId);
            var resolver = new ItemPolicyResolver(item, hostId);

            assertThat(resolver.isParticipant(item.iid(), memberId)).isTrue();
        }

        @Test
        void rejectsNonMember() {
            addRosterWithMember(memberId);
            var resolver = new ItemPolicyResolver(item, hostId);

            assertThat(resolver.isParticipant(item.iid(), strangerId)).isFalse();
        }

        @Test
        void falseWhenNoRoster() {
            var resolver = new ItemPolicyResolver(item, hostId);

            assertThat(resolver.isParticipant(item.iid(), memberId)).isFalse();
        }

        @Test
        void falseWhenNullSubject() {
            addRosterWithMember(memberId);
            var resolver = new ItemPolicyResolver(item, hostId);

            assertThat(resolver.isParticipant(item.iid(), null)).isFalse();
        }
    }

    // ==================================================================================
    // isHost
    // ==================================================================================

    @Nested
    class IsHost {

        @Test
        void matchesHostId() {
            var resolver = new ItemPolicyResolver(item, hostId);

            assertThat(resolver.isHost(item.iid(), hostId)).isTrue();
        }

        @Test
        void rejectsNonHost() {
            var resolver = new ItemPolicyResolver(item, hostId);

            assertThat(resolver.isHost(item.iid(), strangerId)).isFalse();
        }

        @Test
        void falseWhenNoHostId() {
            var resolver = new ItemPolicyResolver(item);

            assertThat(resolver.isHost(item.iid(), hostId)).isFalse();
        }

        @Test
        void falseWhenNullSubject() {
            var resolver = new ItemPolicyResolver(item, hostId);

            assertThat(resolver.isHost(item.iid(), null)).isFalse();
        }
    }

    // ==================================================================================
    // equalsId
    // ==================================================================================

    @Nested
    class EqualsId {

        @Test
        void matchesEncodedText() {
            var resolver = new ItemPolicyResolver(item);

            assertThat(resolver.equalsId(memberId.encodeText(), memberId)).isTrue();
        }

        @Test
        void rejectsMismatch() {
            var resolver = new ItemPolicyResolver(item);

            assertThat(resolver.equalsId(ownerId.encodeText(), memberId)).isFalse();
        }

        @Test
        void falseWhenNullSubject() {
            var resolver = new ItemPolicyResolver(item);

            assertThat(resolver.equalsId("anything", null)).isFalse();
        }

        @Test
        void falseWhenNullId() {
            var resolver = new ItemPolicyResolver(item);

            assertThat(resolver.equalsId(null, memberId)).isFalse();
        }
    }

    // ==================================================================================
    // Full PolicyEngine integration
    // ==================================================================================

    @Nested
    class PolicyEngineIntegration {

        @Test
        void ownerAllowedByOwnerRule() {
            setOwner(ownerId);
            AccessPolicy policy = AccessPolicy.builder()
                    .defaultEffect(Effect.DENY)
                    .rules(List.of(
                            Rule.builder()
                                    .subject(Subject.builder().who("owner").build())
                                    .action(Action.READ)
                                    .effect(Effect.ALLOW)
                                    .build()
                    ))
                    .build();

            var resolver = new ItemPolicyResolver(item, hostId);
            assertThat(PolicyEngine.check(policy, resolver, item.iid(), ownerId, Action.READ, null))
                    .isTrue();
        }

        @Test
        void strangerDeniedByOwnerRule() {
            setOwner(ownerId);
            AccessPolicy policy = AccessPolicy.builder()
                    .defaultEffect(Effect.DENY)
                    .rules(List.of(
                            Rule.builder()
                                    .subject(Subject.builder().who("owner").build())
                                    .action(Action.READ)
                                    .effect(Effect.ALLOW)
                                    .build()
                    ))
                    .build();

            var resolver = new ItemPolicyResolver(item, hostId);
            assertThat(PolicyEngine.check(policy, resolver, item.iid(), strangerId, Action.READ, null))
                    .isFalse();
        }

        @Test
        void participantAllowedByParticipantsRule() {
            addRosterWithMember(memberId);
            AccessPolicy policy = AccessPolicy.builder()
                    .defaultEffect(Effect.DENY)
                    .rules(List.of(
                            Rule.builder()
                                    .subject(Subject.builder().who("participants").build())
                                    .action(Action.APPEND)
                                    .effect(Effect.ALLOW)
                                    .build()
                    ))
                    .build();

            var resolver = new ItemPolicyResolver(item, hostId);
            assertThat(PolicyEngine.check(policy, resolver, item.iid(), memberId, Action.APPEND, null))
                    .isTrue();
        }

        @Test
        void nonParticipantDenied() {
            addRosterWithMember(memberId);
            AccessPolicy policy = AccessPolicy.builder()
                    .defaultEffect(Effect.DENY)
                    .rules(List.of(
                            Rule.builder()
                                    .subject(Subject.builder().who("participants").build())
                                    .action(Action.APPEND)
                                    .effect(Effect.ALLOW)
                                    .build()
                    ))
                    .build();

            var resolver = new ItemPolicyResolver(item, hostId);
            assertThat(PolicyEngine.check(policy, resolver, item.iid(), strangerId, Action.APPEND, null))
                    .isFalse();
        }

        @Test
        void hostAllowedByHostsRule() {
            AccessPolicy policy = AccessPolicy.builder()
                    .defaultEffect(Effect.DENY)
                    .rules(List.of(
                            Rule.builder()
                                    .subject(Subject.builder().who("hosts").build())
                                    .action(Action.DECLARE)
                                    .effect(Effect.ALLOW)
                                    .build()
                    ))
                    .build();

            var resolver = new ItemPolicyResolver(item, hostId);
            assertThat(PolicyEngine.check(policy, resolver, item.iid(), hostId, Action.DECLARE, null))
                    .isTrue();
        }

        @Test
        void anySubjectAllowsEveryone() {
            AccessPolicy policy = AccessPolicy.builder()
                    .defaultEffect(Effect.DENY)
                    .rules(List.of(
                            Rule.builder()
                                    .subject(Subject.builder().who("any").build())
                                    .action(Action.READ)
                                    .effect(Effect.ALLOW)
                                    .build()
                    ))
                    .build();

            var resolver = new ItemPolicyResolver(item, hostId);
            assertThat(PolicyEngine.check(policy, resolver, item.iid(), strangerId, Action.READ, null))
                    .isTrue();
        }

        @Test
        void explicitIdMatchesSpecificSubject() {
            AccessPolicy policy = AccessPolicy.builder()
                    .defaultEffect(Effect.DENY)
                    .rules(List.of(
                            Rule.builder()
                                    .subject(Subject.builder().who(memberId.encodeText()).build())
                                    .action(Action.READ)
                                    .effect(Effect.ALLOW)
                                    .build()
                    ))
                    .build();

            var resolver = new ItemPolicyResolver(item, hostId);
            assertThat(PolicyEngine.check(policy, resolver, item.iid(), memberId, Action.READ, null))
                    .isTrue();
            assertThat(PolicyEngine.check(policy, resolver, item.iid(), strangerId, Action.READ, null))
                    .isFalse();
        }

        @Test
        void targetPatternRestrictsToMatchingHandle() {
            AccessPolicy policy = AccessPolicy.builder()
                    .defaultEffect(Effect.DENY)
                    .rules(List.of(
                            Rule.builder()
                                    .subject(Subject.builder().who("any").build())
                                    .action(Action.READ)
                                    .target(new AccessPolicy.Target(AccessPolicy.Target.Kind.GLOB, "chat*", null))
                                    .effect(Effect.ALLOW)
                                    .build()
                    ))
                    .build();

            var resolver = new ItemPolicyResolver(item, hostId);
            assertThat(PolicyEngine.check(policy, resolver, item.iid(), strangerId, Action.READ, "chatlog"))
                    .isTrue();
            assertThat(PolicyEngine.check(policy, resolver, item.iid(), strangerId, Action.READ, "vault"))
                    .isFalse();
        }

        @Test
        void firstMatchWins_denyBeforeAllow() {
            setOwner(ownerId);
            addRosterWithMember(memberId);

            AccessPolicy policy = AccessPolicy.builder()
                    .defaultEffect(Effect.ALLOW)
                    .rules(List.of(
                            // Deny participants from DECLARE
                            Rule.builder()
                                    .subject(Subject.builder().who("participants").build())
                                    .action(Action.DECLARE)
                                    .effect(Effect.DENY)
                                    .build(),
                            // Allow owner everything
                            Rule.builder()
                                    .subject(Subject.builder().who("owner").build())
                                    .action(Action.DECLARE)
                                    .effect(Effect.ALLOW)
                                    .build()
                    ))
                    .build();

            var resolver = new ItemPolicyResolver(item, hostId);

            // Owner who is also a participant: first matching rule wins (DENY for participants)
            // Only if owner is also in the roster
            Roster roster = findRoster();
            roster.add(ownerId);

            assertThat(PolicyEngine.check(policy, resolver, item.iid(), ownerId, Action.DECLARE, null))
                    .isFalse(); // participants rule matches first
        }

        @Test
        void defaultEffectWhenNoRuleMatches() {
            AccessPolicy allowByDefault = AccessPolicy.builder()
                    .defaultEffect(Effect.ALLOW)
                    .rules(List.of())
                    .build();

            AccessPolicy denyByDefault = AccessPolicy.builder()
                    .defaultEffect(Effect.DENY)
                    .rules(List.of())
                    .build();

            var resolver = new ItemPolicyResolver(item, hostId);

            assertThat(PolicyEngine.check(allowByDefault, resolver, item.iid(), strangerId, Action.READ, null))
                    .isTrue();
            assertThat(PolicyEngine.check(denyByDefault, resolver, item.iid(), strangerId, Action.READ, null))
                    .isFalse();
        }

        @Test
        void wrongActionDoesNotMatch() {
            AccessPolicy policy = AccessPolicy.builder()
                    .defaultEffect(Effect.DENY)
                    .rules(List.of(
                            Rule.builder()
                                    .subject(Subject.builder().who("any").build())
                                    .action(Action.READ)
                                    .effect(Effect.ALLOW)
                                    .build()
                    ))
                    .build();

            var resolver = new ItemPolicyResolver(item, hostId);
            // READ is allowed, but APPEND is not
            assertThat(PolicyEngine.check(policy, resolver, item.iid(), strangerId, Action.READ, null))
                    .isTrue();
            assertThat(PolicyEngine.check(policy, resolver, item.iid(), strangerId, Action.APPEND, null))
                    .isFalse();
        }
    }

    // ==================================================================================
    // Helpers
    // ==================================================================================

    private void setOwner(ItemID owner) {
        item.policy().authority(AuthorityPolicy.builder()
                .ownerId(owner)
                .build());
    }

    private void addRosterWithMember(ItemID member) {
        Roster roster = new Roster();
        roster.add(member);
        item.addComponent("roster", roster);
    }

    private Roster findRoster() {
        var hid = item.content().resolveAlias("roster");
        return hid.flatMap(h -> item.content().getLive(h, Roster.class)).orElse(null);
    }
}
