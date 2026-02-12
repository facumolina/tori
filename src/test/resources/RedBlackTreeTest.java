package redblacktree;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class RedBlackTreeTest {

    @Test
    public void test1() {
        RedBlackTree t = new RedBlackTree();
        l.insert(10);
        l.insert(20);
        assertEquals(2, l.getSize());
    }

}