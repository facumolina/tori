package com.example;

/**
 * A simple linked-list implementation for storing integers.
 */
public class IntsList {
    
    /**
     * Inner class representing a node in the linked list.
     */
    public static class Node {
        public int item;
        public Node next;
        
        public Node(int item, Node next) {
            this.item = item;
            this.next = next;
        }
    }
    
    private Node header;
    private int size;
    
    /**
     * Creates an empty IntsList.
     */
    public IntsList() {
        this.header = null;
        this.size = 0;
    }
    
    /**
     * Adds an integer to the list.
     */
    public void add(int value) {
        if (header == null) {
            header = new Node(value, null);
        } else {
            Node current = header;
            while (current.next != null) {
                current = current.next;
            }
            current.next = new Node(value, null);
        }
        size++;
    }
    
    /**
     * Returns the size of the list.
     */
    public int getSize() {
        return size;
    }
    
    /**
     * Returns the first element of the list, or -1 if empty.
     */
    public int getFirst() {
        if (header == null) {
            return -1;
        }
        return header.item;
    }
    
    /**
     * Returns the element at the given index, or -1 if index is out of bounds.
     */
    public int get(int index) {
        if (index < 0 || index >= size) {
            return -1;
        }
        Node current = header;
        for (int i = 0; i < index; i++) {
            current = current.next;
        }
        return current.item;
    }
    
    /**
     * Returns true if the list is empty.
     */
    public boolean isEmpty() {
        return header == null;
    }
    
    /**
     * Returns the header node (for testing purposes).
     */
    public Node getHeader() {
        return header;
    }
}
