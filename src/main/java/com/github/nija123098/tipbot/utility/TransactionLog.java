package com.github.nija123098.tipbot.utility;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Collections;

public class TransactionLog {
    private static final Path PATH;
    static {
        PATH = Paths.get("Transaction-Log-" + System.currentTimeMillis());
    }
    public static void log(String log) throws IOException {
        //TODO: don't get why this is dumped into a file when we are using db elsewhere
        Files.write(PATH, Collections.singletonList(log), StandardOpenOption.APPEND, StandardOpenOption.CREATE);
    }
}
