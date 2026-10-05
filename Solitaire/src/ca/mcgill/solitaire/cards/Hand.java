package ca.mcgill.solitaire.cards;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A mutable collection of cards that can be compared as poker hands.
 *
 * Handling of hands that do not have exactly five cards:
 * - Fewer than 5 cards: only the categories that can exist with that many
 *   cards are recognized (high card, pair, two pair, three/four of a kind).
 *   Straight, flush and full house require exactly five cards.
 * - More than 5 cards (e.g., Texas Hold'em): the strongest 5-card
 *   combination is used.
 * - When two hands have the same type and identical tie-breakers, the one
 *   with fewer cards is considered weaker.
 *
 * Note: compareTo is not consistent with equals (two different hands can be
 * equal in poker strength).
 */
public class Hand implements Comparable<Hand> {
    /** Poker hand types, from weakest to strongest. */
    public enum PokerType {
        HIGH_CARD, ONE_PAIR, TWO_PAIR, THREE_OF_A_KIND, STRAIGHT, FLUSH, FULL_HOUSE, FOUR_OF_A_KIND,
        STRAIGHT_FLUSH
    }

    private static final int HAND_SIZE = 5;
    private static final int ACE_HIGH = 14;

    /*
     * Type plus an ordered list of values used to break ties (most significant first).
     */
    private record Evaluation(PokerType type, List<Integer> tieBreakers) implements Comparable<Evaluation> {
        @Override
        public int compareTo(Evaluation pOther) {
            int result = type.compareTo(pOther.type);
            if (result != 0) {
                return result;
            }
            int common = Math.min(tieBreakers.size(), pOther.tieBreakers.size());
            for (int i = 0; i < common; i++) {
                result = Integer.compare(tieBreakers.get(i), pOther.tieBreakers.get(i));
                if (result != 0) {
                    return result;
                }
            }
            return Integer.compare(tieBreakers.size(), pOther.tieBreakers.size());
        }
    }

    private final List<Card> aCards = new ArrayList<>();

    /**
     * @param pCard The card to add.
     * @pre pCard != null && !contains(pCard)
     */
    public void add(Card pCard) {
        assert pCard != null && !aCards.contains(pCard);
        aCards.add(pCard);
    }

    /**
     * @param pCard The card to remove.
     * @pre contains(pCard)
     */
    public void remove(Card pCard) {
        assert contains(pCard);
        aCards.remove(pCard);
    }

    public boolean contains(Card pCard) {
        return aCards.contains(pCard);
    }

    public int size() {
        return aCards.size();
    }

    /**
     * @return The poker type of this hand (best 5-card subset if more than 5 cards).
     * @pre size() > 0
     */
    public PokerType pokerType() {
        return bestEvaluation().type();
    }

    /**
     * @pre size() > 0 && pOther.size() > 0
     */
    @Override
    public int compareTo(Hand pOther) {
        return bestEvaluation().compareTo(pOther.bestEvaluation());
    }

    private Evaluation bestEvaluation() {
        assert !aCards.isEmpty();
        if (aCards.size() <= HAND_SIZE) {
            return evaluate(aCards);
        }
        return bestOf(aCards, 0, new ArrayList<>());
    }

    /* Enumerates all 5-card combinations and keeps the strongest. */
    private static Evaluation bestOf(List<Card> pCards, int pStart, List<Card> pChosen) {
        if (pChosen.size() == HAND_SIZE) {
            return evaluate(pChosen);
        }
        Evaluation best = null;
        int last = pCards.size() - (HAND_SIZE - pChosen.size());
        for (int i = pStart; i <= last; i++) {
            pChosen.add(pCards.get(i));
            Evaluation candidate = bestOf(pCards, i + 1, pChosen);
            pChosen.remove(pChosen.size() - 1);
            if (best == null || candidate.compareTo(best) > 0) {
                best = candidate;
            }
        }
        return best;
    }

    /* Ace counts as high (14) here; the ace-low straight is handled separately. */
    private static int value(Rank pRank) {
        return pRank == Rank.ACE ? ACE_HIGH : pRank.ordinal() + 1;
    }

    private static Evaluation evaluate(List<Card> pCards) {
        Map<Integer, Integer> counts = new HashMap<>();
        for (Card card : pCards) {
            counts.merge(value(card.rank()), 1, Integer::sum);
        }
        // Groups sorted by multiplicity, then by value, both descending
        List<Map.Entry<Integer, Integer>> groups = new ArrayList<>(counts.entrySet());
        groups.sort((a, b) -> {
            int result = Integer.compare(b.getValue(), a.getValue());
            return result != 0 ? result : Integer.compare(b.getKey(), a.getKey());
        });
        List<Integer> values = groups.stream().map(Map.Entry::getKey).toList();
        int top = groups.get(0).getValue();
        int second = groups.size() > 1 ? groups.get(1).getValue() : 0;

        boolean flush = pCards.size() == HAND_SIZE
                && pCards.stream().allMatch(c -> c.suit() == pCards.get(0).suit());

        int straightHigh = straightHigh(values, pCards.size());
        boolean straight = straightHigh > 0;
        List<Integer> straightKey = List.of(straightHigh);

        if (straight && flush) {
            return new Evaluation(PokerType.STRAIGHT_FLUSH, straightKey);
        }
        if (top == 4) {
            return new Evaluation(PokerType.FOUR_OF_A_KIND, values);
        }
        if (top == 3 && second == 2) {
            return new Evaluation(PokerType.FULL_HOUSE, values);
        }
        if (flush) {
            return new Evaluation(PokerType.FLUSH, values);
        }
        if (straight) {
            return new Evaluation(PokerType.STRAIGHT, straightKey);
        }
        if (top == 3) {
            return new Evaluation(PokerType.THREE_OF_A_KIND, values);
        }
        if (top == 2 && second == 2) {
            return new Evaluation(PokerType.TWO_PAIR, values);
        }
        if (top == 2) {
            return new Evaluation(PokerType.ONE_PAIR, values);
        }
        return new Evaluation(PokerType.HIGH_CARD, values);
    }

    /*
     * Returns the high card of the straight (5 for A-2-3-4-5), or 0 if the cards
     * do not form a straight. pValues is sorted in descending order.
     */
    private static int straightHigh(List<Integer> pValues, int pCardCount) {
        if (pCardCount != HAND_SIZE || pValues.size() != HAND_SIZE) {
            return 0;
        }
        if (pValues.get(0) - pValues.get(HAND_SIZE - 1) == HAND_SIZE - 1) {
            return pValues.get(0);
        }
        if (pValues.equals(List.of(ACE_HIGH, 5, 4, 3, 2))) {
            return 5; // Ace-low straight ("wheel")
        }
        return 0;
    }
}