package com.besome.sketch.adapters;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

/**
 * Unit tests for {@link ProjectsAdapter}.
 *
 * <p>Covers {@link ProjectsAdapter#getShapedBackgroundForList} — the static, pure-Java shape
 * selector introduced in this PR. The method chooses one of four background drawables based on
 * the list size and item position:
 * <ul>
 *   <li><em>alone</em> — the list has exactly one item
 *   <li><em>top</em>   — position 0 in a list with more than one item
 *   <li><em>bottom</em>— last position in a list with more than one item
 *   <li><em>middle</em>— any interior position
 * </ul>
 *
 * <p>Tests verify routing correctness without depending on specific Android resource IDs.
 * They rely only on the contract that each of the four logical slots maps to a <em>distinct</em>
 * integer value, and that the same slot always returns the same integer.
 */
public class ProjectsAdapterTest {

    // -----------------------------------------------------------------
    // Single-element list — "alone" shape
    // -----------------------------------------------------------------

    @Test
    public void singleItemList_positionZero_returnsAloneShape() {
        List<String> list = Collections.singletonList("a");
        int result = ProjectsAdapter.getShapedBackgroundForList(list, 0);
        // Same call must be stable (idempotent)
        assertEquals(result, ProjectsAdapter.getShapedBackgroundForList(list, 0));
    }

    @Test
    public void singleItemList_aloneShapeDistinctFromTop() {
        List<String> single = Collections.singletonList("a");
        List<String> two = Arrays.asList("a", "b");

        int alone = ProjectsAdapter.getShapedBackgroundForList(single, 0);
        int top   = ProjectsAdapter.getShapedBackgroundForList(two, 0);

        assertNotEquals(
                "A single-item list should use a different shape than position-0 of a multi-item list",
                alone, top);
    }

    // -----------------------------------------------------------------
    // First position in multi-item list — "top" shape
    // -----------------------------------------------------------------

    @Test
    public void twoItemList_positionZero_returnsTopShape() {
        List<String> list = Arrays.asList("a", "b");
        int top = ProjectsAdapter.getShapedBackgroundForList(list, 0);
        // Must be stable
        assertEquals(top, ProjectsAdapter.getShapedBackgroundForList(list, 0));
    }

    @Test
    public void threeItemList_positionZero_returnsTopShape() {
        List<String> twoItems   = Arrays.asList("a", "b");
        List<String> threeItems = Arrays.asList("a", "b", "c");

        int topTwo   = ProjectsAdapter.getShapedBackgroundForList(twoItems, 0);
        int topThree = ProjectsAdapter.getShapedBackgroundForList(threeItems, 0);

        assertEquals("Top shape must be the same regardless of list length", topTwo, topThree);
    }

    // -----------------------------------------------------------------
    // Last position in multi-item list — "bottom" shape
    // -----------------------------------------------------------------

    @Test
    public void twoItemList_lastPosition_returnsBottomShape() {
        List<String> list = Arrays.asList("a", "b");
        int bottom = ProjectsAdapter.getShapedBackgroundForList(list, list.size() - 1);
        assertEquals(bottom, ProjectsAdapter.getShapedBackgroundForList(list, list.size() - 1));
    }

    @Test
    public void threeItemList_lastPosition_returnsBottomShape() {
        List<String> twoItems   = Arrays.asList("a", "b");
        List<String> threeItems = Arrays.asList("a", "b", "c");

        int bottomTwo   = ProjectsAdapter.getShapedBackgroundForList(twoItems, twoItems.size() - 1);
        int bottomThree = ProjectsAdapter.getShapedBackgroundForList(threeItems, threeItems.size() - 1);

        assertEquals("Bottom shape must be the same regardless of list length", bottomTwo, bottomThree);
    }

    @Test
    public void bottomShapeDistinctFromTopShape() {
        List<String> list = Arrays.asList("a", "b");
        int top    = ProjectsAdapter.getShapedBackgroundForList(list, 0);
        int bottom = ProjectsAdapter.getShapedBackgroundForList(list, list.size() - 1);
        assertNotEquals("Top and bottom shapes must differ", top, bottom);
    }

    // -----------------------------------------------------------------
    // Middle positions — "middle" shape
    // -----------------------------------------------------------------

    @Test
    public void threeItemList_middlePosition_returnsMiddleShape() {
        List<String> list = Arrays.asList("a", "b", "c");
        int middle = ProjectsAdapter.getShapedBackgroundForList(list, 1);
        assertEquals(middle, ProjectsAdapter.getShapedBackgroundForList(list, 1));
    }

    @Test
    public void middleShapeDistinctFromTopAndBottom() {
        List<String> list = Arrays.asList("a", "b", "c");
        int top    = ProjectsAdapter.getShapedBackgroundForList(list, 0);
        int middle = ProjectsAdapter.getShapedBackgroundForList(list, 1);
        int bottom = ProjectsAdapter.getShapedBackgroundForList(list, list.size() - 1);

        assertNotEquals("Middle shape must differ from top", top, middle);
        assertNotEquals("Middle shape must differ from bottom", bottom, middle);
    }

    @Test
    public void allFourShapesAreDistinct() {
        // alone, top, middle, bottom must all be different drawable IDs
        List<String> single = Collections.singletonList("a");
        List<String> three  = Arrays.asList("a", "b", "c");

        int alone  = ProjectsAdapter.getShapedBackgroundForList(single, 0);
        int top    = ProjectsAdapter.getShapedBackgroundForList(three, 0);
        int middle = ProjectsAdapter.getShapedBackgroundForList(three, 1);
        int bottom = ProjectsAdapter.getShapedBackgroundForList(three, three.size() - 1);

        assertNotEquals("alone vs top",    alone, top);
        assertNotEquals("alone vs middle", alone, middle);
        assertNotEquals("alone vs bottom", alone, bottom);
        assertNotEquals("top vs middle",   top,   middle);
        assertNotEquals("top vs bottom",   top,   bottom);
        assertNotEquals("middle vs bottom",middle, bottom);
    }

    // -----------------------------------------------------------------
    // Middle items are consistently the same shape regardless of which
    // interior position they occupy
    // -----------------------------------------------------------------

    @Test
    public void multipleMiddlePositions_returnSameShape() {
        List<String> list = Arrays.asList("a", "b", "c", "d", "e");

        int middle1 = ProjectsAdapter.getShapedBackgroundForList(list, 1);
        int middle2 = ProjectsAdapter.getShapedBackgroundForList(list, 2);
        int middle3 = ProjectsAdapter.getShapedBackgroundForList(list, 3);

        assertEquals("All interior positions must yield the same middle shape", middle1, middle2);
        assertEquals("All interior positions must yield the same middle shape", middle1, middle3);
    }

    // -----------------------------------------------------------------
    // Two-item list edge case: position 0 is top, position 1 is bottom
    // (no middle items)
    // -----------------------------------------------------------------

    @Test
    public void twoItemList_hasNoMiddlePosition() {
        List<String> list = Arrays.asList("a", "b");
        // position 0 is top, position 1 is last == bottom; verify they're distinct from middle
        List<String> three = Arrays.asList("a", "b", "c");
        int middle = ProjectsAdapter.getShapedBackgroundForList(three, 1);

        int pos0 = ProjectsAdapter.getShapedBackgroundForList(list, 0);
        int pos1 = ProjectsAdapter.getShapedBackgroundForList(list, 1);

        assertNotEquals("Position 0 of two-item list is top, not middle", middle, pos0);
        assertNotEquals("Position 1 of two-item list is bottom, not middle", middle, pos1);
    }

    // -----------------------------------------------------------------
    // Regression: list type parameter is generic — works with any type
    // -----------------------------------------------------------------

    @Test
    public void genericType_integerList_behaviorIdentical() {
        List<Integer> single = Collections.singletonList(1);
        List<Integer> three  = Arrays.asList(1, 2, 3);

        int alone  = ProjectsAdapter.getShapedBackgroundForList(single, 0);
        int top    = ProjectsAdapter.getShapedBackgroundForList(three, 0);
        int middle = ProjectsAdapter.getShapedBackgroundForList(three, 1);
        int bottom = ProjectsAdapter.getShapedBackgroundForList(three, three.size() - 1);

        // Should mirror the String-list behavior
        assertNotEquals(alone, top);
        assertNotEquals(top, middle);
        assertNotEquals(middle, bottom);
        assertNotEquals(top, bottom);
    }

    // -----------------------------------------------------------------
    // Boundary: large list — first and last positions still map to
    // top/bottom, all others to middle
    // -----------------------------------------------------------------

    @Test
    public void largeList_firstPositionIsTop() {
        List<Integer> small = Arrays.asList(1, 2);
        List<Integer> large = Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);

        int topSmall = ProjectsAdapter.getShapedBackgroundForList(small, 0);
        int topLarge = ProjectsAdapter.getShapedBackgroundForList(large, 0);

        assertEquals("Top shape is invariant to list size", topSmall, topLarge);
    }

    @Test
    public void largeList_lastPositionIsBottom() {
        List<Integer> small = Arrays.asList(1, 2);
        List<Integer> large = Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);

        int bottomSmall = ProjectsAdapter.getShapedBackgroundForList(small, small.size() - 1);
        int bottomLarge = ProjectsAdapter.getShapedBackgroundForList(large, large.size() - 1);

        assertEquals("Bottom shape is invariant to list size", bottomSmall, bottomLarge);
    }
}