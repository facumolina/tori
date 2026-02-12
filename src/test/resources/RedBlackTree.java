package redblacktree;

import java.util.Set;

@SuppressWarnings("unchecked")
public class RedBlackTree {

    private RedBlackTreeNode root = null;

    private int size = 0;

    private static final int RED = 0;

    private static final int BLACK = 1;

    

    /*
     * Builders ---------------------------
     */
    public RedBlackTree() {
        root = null;
        size = 0;
    }

    public void insert(int newKey) {
        RedBlackTreeNode z = new RedBlackTreeNode();
        z.key = newKey;

        RedBlackTreeNode y = null;
        for (RedBlackTreeNode x = root; x != null;) {
            y = x;
            if (x.key==z.key)
                return;
            if (x.key>z.key)
                x = x.left;
            else
                x = x.right;
        }

        z.parent = y;
        z.left = z.right = null;

        if (y==null) {
            root = z;
        } else {
            if (y.key==z.key)
                return;

            z.color = RED;

            if (y.key>z.key)	{ y.left = z; }
            else 				{ y.right = z; }

            insertFixUp(z);
        }

        size++;

    }

    private final RedBlackTreeNode parentOf(RedBlackTreeNode n) { 
        return n==null ? null : n.parent; 
    }

    private final RedBlackTreeNode leftOf(RedBlackTreeNode n) { 
        return n==null ? null : n.left; 
    }

    private final RedBlackTreeNode rightOf(RedBlackTreeNode n) { 
        return n==null ? null : n.right; \
    }
    
    private final int colorOf(RedBlackTreeNode n) { 
        return n==null ? BLACK : n.color; 
    }
    
    private final void setColor(RedBlackTreeNode n, int color) { 
        if (n!=null) n.color = color; 
    }

    private void insertFixUp(RedBlackTreeNode z) {
        while (z != null && z != root && z.parent.color == RED) {
            if (parentOf(z) == leftOf(parentOf(parentOf(z)))) {
                RedBlackTreeNode y = rightOf(parentOf(parentOf(z)));
                if (colorOf(y) == RED) {
                    setColor(parentOf(z), BLACK);
                    setColor(y, BLACK);
                    setColor(parentOf(parentOf(z)), RED);
                    z = parentOf(parentOf(z));
                } else {
                    if (z == rightOf(parentOf(z))) {
                        z = parentOf(z);
                        rotateLeft(z);
                    }
                    setColor(parentOf(z), BLACK);
                    setColor(parentOf(parentOf(z)), RED);
                    if (parentOf(parentOf(z)) != null)
                        rotateRight(parentOf(parentOf(z)));
                }
            } else {
                RedBlackTreeNode y = leftOf(parentOf(parentOf(z)));
                if (colorOf(y) == RED) {
                    setColor(parentOf(z), BLACK);
                    setColor(y, BLACK);
                    setColor(parentOf(parentOf(z)), RED);
                    z = parentOf(parentOf(z));
                } else {
                    if (z == leftOf(parentOf(z))) {
                        z = parentOf(z);
                        rotateRight(z);
                    }
                    setColor(parentOf(z),  BLACK);
                    setColor(parentOf(parentOf(z)), RED);
                    if (parentOf(parentOf(z)) != null)
                        rotateLeft(parentOf(parentOf(z)));
                }
            }
        }
        root.color = BLACK;
    }

    /**
     * From CLR.
     */
    private void rotateLeft(RedBlackTreeNode x) {
        RedBlackTreeNode y = x.right;
        x.right = y.left;
        if (y.left != null)
            y.left.parent = x;
        y.parent = x.parent;
        if (x.parent == null)
            root = y;
        else if (x.parent.left == x)
            x.parent.left = y;
        else
            x.parent.right = y;
        y.left = x;
        x.parent = y;
    }

    /**
     * From CLR.
     */
    private void rotateRight(RedBlackTreeNode x) {
        RedBlackTreeNode y = x.left;
        x.left = y.right;
        if (y.right != null)
            y.right.parent = x;
        y.parent = x.parent;
        if (x.parent == null)
            root = y;
        else if (x.parent.right == x)
            x.parent.right = y;
        else
            x.parent.left = y;
        y.right = x;
        x.parent = y;
    }
    
    public int getSize() { 
        return size; 
    }
    
}