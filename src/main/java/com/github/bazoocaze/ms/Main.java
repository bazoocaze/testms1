package com.github.bazoocaze.ms;

import com.github.bazoocaze.lib.App;

/**
 * Simple microservice that calls testlib1 and prints a hello world.
 */
public class Main {
    public static void main(String[] args) {
        int answer = App.getAnswer();
        System.out.println("Hello World! The answer is " + answer);
    }
}
