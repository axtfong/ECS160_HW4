package com.ecs160;

import com.ecs160.annotations.Endpoint;
import com.ecs160.annotations.Microservice;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

public class AppTest {
    private Launcher launcher;

    @Before
    public void setUp() {
        launcher = new Launcher();
    }

    @Test
    public void testRegisterMicroservice() throws Exception {
        launcher.registerMicroservice(TestMicroservice.class);
        assertNotNull("Launcher should be created", launcher);
    }

    @Test
    public void testRegisterMultipleMicroservices() throws Exception {
        launcher.registerMicroservice(TestMicroservice.class, AnotherTestMicroservice.class);
        assertNotNull("Launcher should be created", launcher);
    }

    @Test(expected = RuntimeException.class)
    public void testRegisterMicroserviceWithInvalidReturnType() throws Exception {
        launcher.registerMicroservice(InvalidReturnTypeMicroservice.class);
    }

    @Test(expected = RuntimeException.class)
    public void testRegisterMicroserviceWithInvalidParameterCount() throws Exception {
        launcher.registerMicroservice(InvalidParameterMicroservice.class);
    }

    @Test
    public void testLaunchWithoutRegistration() {
        boolean result = launcher.launch(9999);
        assertFalse("Should return false when no endpoints registered", result);
    }

    @Test
    public void testStop() {
        launcher.stop();
        assertNotNull("Launcher should still exist after stop", launcher);
    }

    @Test
    public void testRegisterMicroserviceWithValidEndpoint() throws Exception {
        launcher.registerMicroservice(TestMicroservice.class);
        assertNotNull("Launcher should be created", launcher);
    }

    @Test
    public void testMultipleEndpointsInSameMicroservice() throws Exception {
        launcher.registerMicroservice(MultiEndpointMicroservice.class);
        assertNotNull("Launcher should be created", launcher);
    }

    @Microservice
    static class TestMicroservice {
        @Endpoint(url = "test_endpoint")
        public String handleRequest(String input) {
            return "Response: " + input;
        }
    }

    @Microservice
    static class AnotherTestMicroservice {
        @Endpoint(url = "another_endpoint")
        public String handleRequest(String input) {
            return "Another: " + input;
        }
    }

    @Microservice
    static class InvalidReturnTypeMicroservice {
        @Endpoint(url = "invalid")
        public int handleRequest(String input) { // Wrong return type
            return 42;
        }
    }

    @Microservice
    static class InvalidParameterMicroservice {
        @Endpoint(url = "invalid")
        public String handleRequest(String input, String extra) { // Too many parameters
            return "test";
        }
    }

    @Microservice
    static class MultiEndpointMicroservice {
        @Endpoint(url = "endpoint1")
        public String handleRequest1(String input) {
            return "Response 1: " + input;
        }

        @Endpoint(url = "endpoint2")
        public String handleRequest2(String input) {
            return "Response 2: " + input;
        }
    }
}
