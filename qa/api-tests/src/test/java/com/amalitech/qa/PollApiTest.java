package com.amalitech.qa;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.testng.annotations.*;
import static io.restassured.RestAssured.*;
import static org.hamcrest.Matchers.*;

public class PollApiTest {
    private String authToken;

    @BeforeClass
    public void setup() {
        RestAssured.baseURI = "http://localhost:8080";
        authToken = given()
            .contentType(ContentType.JSON)
            .body("{\"email\":\"admin@amalitech.com\",\"password\":\"password123\"}")
        .when().post("/api/auth/login")
        .then().extract().path("token");
    }

    @Test
    public void testGetAllPolls() {
        given().when().get("/api/polls")
        .then().statusCode(200).body("content", notNullValue());
    }

    @Test
    public void testCreatePoll() {
        given()
            .contentType(ContentType.JSON)
            .header("Authorization", "Bearer " + authToken)
            .body("{\"question\":\"Test Poll?\",\"options\":[\"Yes\",\"No\"],\"multipleChoice\":false}")
        .when().post("/api/polls")
        .then().statusCode(200).body("question", equalTo("Test Poll?"));
    }

    // TODO: testVoteOnPoll
    // TODO: testClosePoll
    // TODO: testCreatePollUnauthorized
}
