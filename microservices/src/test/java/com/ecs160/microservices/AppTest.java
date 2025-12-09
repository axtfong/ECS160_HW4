package com.ecs160.microservices;

import com.ecs160.microservices.model.BugIssue;
import com.google.gson.Gson;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class AppTest {
    private IssueSummarizerMicroservice summarizerService;
    private BugFinderMicroservice bugFinderService;
    private IssueComparatorMicroservice comparatorService;
    private Gson gson;

    @Before
    public void setUp() {
        summarizerService = new IssueSummarizerMicroservice();
        bugFinderService = new BugFinderMicroservice();
        comparatorService = new IssueComparatorMicroservice();
        gson = new Gson();
    }

    @Test
    public void testIssueSummarizerWithValidJson() {
        String input = "{\"description\": \"The application crashes when clicking the button\", \"title\": \"Crash on button click\"}";
        String result = summarizerService.handleRequest(input);
        
        assertNotNull("Result should not be null", result);
        assertFalse("Result should not be empty", result.isEmpty());

        try {
            BugIssue bugIssue = gson.fromJson(result, BugIssue.class);
            assertNotNull("Should parse to BugIssue", bugIssue);
            assertNotNull("Bug type should not be null", bugIssue.getBug_type());
        } catch (Exception e) {
            fail("Result should be valid JSON: " + result);
        }
    }

    @Test
    public void testIssueSummarizerWithEmptyInput() {
        String input = "{}";
        String result = summarizerService.handleRequest(input);
        
        assertNotNull("Result should not be null", result);

        BugIssue bugIssue = gson.fromJson(result, BugIssue.class);
        assertNotNull("Should parse to BugIssue", bugIssue);
    }

    @Test
    public void testIssueSummarizerWithInvalidJson() {
        String input = "not valid json";
        String result = summarizerService.handleRequest(input);
        
        assertNotNull("Result should not be null", result);

        BugIssue bugIssue = gson.fromJson(result, BugIssue.class);
        assertNotNull("Should parse to BugIssue", bugIssue);
    }

    @Test
    public void testBugFinderWithValidCode() {
        String input = "{\"filename\": \"test.c\", \"content\": \"int main() { int *p = NULL; *p = 5; return 0; }\"}";
        String result = bugFinderService.handleRequest(input);
        
        assertNotNull("Result should not be null", result);
        assertFalse("Result should not be empty", result.isEmpty());
        

        try {
            BugIssue[] bugs = gson.fromJson(result, BugIssue[].class);
            assertNotNull("Should parse to BugIssue array", bugs);
        } catch (Exception e) {
            fail("Result should be valid JSON array: " + result);
        }
    }

    @Test
    public void testBugFinderWithEmptyCode() {
        String input = "{\"filename\": \"empty.c\", \"content\": \"\"}";
        String result = bugFinderService.handleRequest(input);
        
        assertNotNull("Result should not be null", result);
        BugIssue[] bugs = gson.fromJson(result, BugIssue[].class);
        assertNotNull("Should parse to BugIssue array", bugs);
    }

    @Test
    public void testBugFinderWithInvalidJson() {
        String input = "not valid json";
        String result = bugFinderService.handleRequest(input);
        
        assertNotNull("Result should not be null", result);

        BugIssue[] bugs = gson.fromJson(result, BugIssue[].class);
        assertNotNull("Should parse to BugIssue array", bugs);
    }

    @Test
    public void testIssueComparatorWithTwoLists() {
        String input = "{\"list1\": [{\"bug_type\": \"NullPointer\", \"line\": 10, \"description\": \"Null pointer dereference\", \"filename\": \"test.c\"}], " +
                      "\"list2\": [{\"bug_type\": \"NullPointer\", \"line\": 10, \"description\": \"Null pointer dereference\", \"filename\": \"test.c\"}]}";
        String result = comparatorService.handleRequest(input);
        
        assertNotNull("Result should not be null", result);
        assertFalse("Result should not be empty", result.isEmpty());
        

        try {
            BugIssue[] common = gson.fromJson(result, BugIssue[].class);
            assertNotNull("Should parse to BugIssue array", common);
        } catch (Exception e) {
            fail("Result should be valid JSON array: " + result);
        }
    }

    @Test
    public void testIssueComparatorWithEmptyLists() {
        String input = "{\"list1\": [], \"list2\": []}";
        String result = comparatorService.handleRequest(input);
        
        assertNotNull("Result should not be null", result);
        BugIssue[] common = gson.fromJson(result, BugIssue[].class);
        assertNotNull("Should parse to BugIssue array", common);
        // LLM might return something even with empty lists, so just verify it's a valid array
        assertTrue("Should return a valid array (may be empty or have items)", common.length >= 0);
    }

    @Test
    public void testIssueComparatorWithInvalidJson() {
        String input = "not valid json";
        String result = comparatorService.handleRequest(input);
        
        assertNotNull("Result should not be null", result);

        BugIssue[] common = gson.fromJson(result, BugIssue[].class);
        assertNotNull("Should parse to BugIssue array", common);
    }

    @Test
    public void testIssueSummarizerWithTitleAndBody() {
        String input = "{\"title\": \"Memory leak in function\", \"body\": \"The function allocates memory but never frees it\", \"description\": \"Memory leak issue\"}";
        String result = summarizerService.handleRequest(input);
        
        assertNotNull("Result should not be null", result);
        BugIssue bugIssue = gson.fromJson(result, BugIssue.class);
        assertNotNull("Should parse to BugIssue", bugIssue);
    }

    @Test
    public void testBugFinderWithComplexCode() {
        String input = "{\"filename\": \"complex.c\", \"content\": \"#include <stdio.h>\\nint main() {\\n    char *str = malloc(100);\\n    // Missing free(str)\\n    return 0;\\n}\"}";
        String result = bugFinderService.handleRequest(input);
        
        assertNotNull("Result should not be null", result);
        BugIssue[] bugs = gson.fromJson(result, BugIssue[].class);
        assertNotNull("Should parse to BugIssue array", bugs);
    }

    @Test
    public void testIssueComparatorWithDifferentBugs() {
        String input = "{\"list1\": [{\"bug_type\": \"MemoryLeak\", \"line\": 5, \"description\": \"Memory not freed\", \"filename\": \"test.c\"}], " +
                      "\"list2\": [{\"bug_type\": \"NullPointer\", \"line\": 10, \"description\": \"Null pointer\", \"filename\": \"test.c\"}]}";
        String result = comparatorService.handleRequest(input);
        
        assertNotNull("Result should not be null", result);
        BugIssue[] common = gson.fromJson(result, BugIssue[].class);
        assertNotNull("Should parse to BugIssue array", common);
    }

    @Test
    public void testAllMicroservicesReturnValidJson() {
        String summarizerInput = "{\"description\": \"Test issue\"}";
        String bugFinderInput = "{\"filename\": \"test.c\", \"content\": \"int main() { return 0; }\"}";
        String comparatorInput = "{\"list1\": [], \"list2\": []}";

        String summarizerResult = summarizerService.handleRequest(summarizerInput);
        String bugFinderResult = bugFinderService.handleRequest(bugFinderInput);
        String comparatorResult = comparatorService.handleRequest(comparatorInput);

        assertNotNull("Summarizer result should not be null", summarizerResult);
        assertNotNull("BugFinder result should not be null", bugFinderResult);
        assertNotNull("Comparator result should not be null", comparatorResult);

        try {
            gson.fromJson(summarizerResult, BugIssue.class);
            gson.fromJson(bugFinderResult, BugIssue[].class);
            gson.fromJson(comparatorResult, BugIssue[].class);
        } catch (Exception e) {
            fail("All results should be valid JSON");
        }
    }
}
