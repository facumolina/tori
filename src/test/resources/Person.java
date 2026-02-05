package com.example;

/**
 * A simple Person class to test field coverage with duplicate field names.
 */
public class Person {
    
    /**
     * Inner class representing an Address.
     */
    public static class Address {
        public String street;
        public String city;
        // Note: 'name' field exists here too
        public String name;  // name of the address (e.g., "Home", "Work")
        
        public Address(String name, String street, String city) {
            this.name = name;
            this.street = street;
            this.city = city;
        }
        
        public String getName() {
            return name;
        }
    }
    
    // Note: 'name' field also exists in the Person class
    private String name;
    private int age;
    private Address address;
    
    /**
     * Creates a Person.
     */
    public Person(String name, int age) {
        this.name = name;
        this.age = age;
        this.address = null;
    }
    
    /**
     * Returns the person's name.
     */
    public String getName() {
        return name;
    }
    
    /**
     * Returns the person's age.
     */
    public int getAge() {
        return age;
    }
    
    /**
     * Sets the address.
     */
    public void setAddress(Address addr) {
        this.address = addr;
    }
    
    /**
     * Returns the address.
     */
    public Address getAddress() {
        return address;
    }
}
