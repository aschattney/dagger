package com.example.dagger.activitygraphs;

import dagger.Lazy;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.Set;

/**
 * Created by Andy on 07.05.2017.
 */
public class SomeClass {

    @Inject
    public SomeClass(Lazy<String> lazy) {

    }

}
