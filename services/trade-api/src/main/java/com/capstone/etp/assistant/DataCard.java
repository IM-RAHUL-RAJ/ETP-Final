package com.capstone.etp.assistant;

import java.util.List;

/**
 * A block of figures shown under the answer. These come straight from the account's data, never from the model.
 * kind TABLE has columns and rows; kind METRICS is label / value pairs (two columns).
 */
public record DataCard(String kind, String title, List<String> columns, List<List<String>> rows) {

    public static DataCard table(String title, List<String> columns, List<List<String>> rows) {
        return new DataCard("TABLE", title, columns, rows);
    }

    public static DataCard metrics(String title, List<List<String>> rows) {
        return new DataCard("METRICS", title, List.of("Measure", "Value"), rows);
    }
}
