package com.example.dagger.simple;

import dagger.Module;
import dagger.Provides;

import javax.inject.Named;

@Module
public abstract class SingletonModule {

    @Provides
    @Named("apiKey")
    public static String someString() {
       return "";
    }

    @Provides
    @Named("apiKey1")
    public static String secondString() {
        return "";
    }
}
