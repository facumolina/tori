package org.other;

/**
 * A simple linked-list implementation for storing integers.
 * This is in a different package to test package-aware functionality.
 */
public class IntsList {
    
    /**
     * Inner class representing a node in the linked list.
     */
    public static class Node {
        public int value;
        public Node link;
        
        public Node(int value, Node link) {
            this.value = value;
            this.link = link;
        }
    }
    
    private Node head;
    private int count;
    
    /**
     * Creates an empty IntsList.
     */
    public IntsList() {
        this.head = null;
        this.count = 0;
    }
    
    /**
     * Adds an integer to the list.
     */
    public void add(int value) {
        if (head == null) {
            head = new Node(value, null);
        } else {
            Node current = head;
            while (current.link != null) {
                current = current.link;
            }
            current.link = new Node(value, null);
        }
        count++;
    }
    
    /**
     * Returns the size of the list.
     */
    public int getCount() {
        return count;
    }
    
    /**
     * Returns the head node (for testing purposes).
     */
    public Node getHead() {
        return head;
    }
}
