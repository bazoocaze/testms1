package com.github.bazoocaze.ms;

import com.github.bazoocaze.lib.App;
import com.github.bazoocaze.lib2.App2;

/**
 * Simple microservice that calls testlib1 and testlib2 and prints a hello world.
 */
public class Main {
    public static void main(String[] args) {
        String answer = App.getAnswer();
        String fullAnswer = App2.getFullAnswer();
        System.out.println("Hello World! The answer is " + answer);
        System.out.println("testlib2 says: \"" + fullAnswer + "\"");
    }
}
