package com.amalitech.qa;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import io.github.bonigarcia.wdm.WebDriverManager;
import org.testng.annotations.*;
import static org.testng.Assert.*;

public class PollPageTest {
    private WebDriver driver;

    @BeforeClass
    public void setup() {
        WebDriverManager.chromedriver().setup();
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless");
        driver = new ChromeDriver(options);
    }

    @Test
    public void testHomepageLoads() {
        driver.get("http://localhost:3000");
        assertTrue(driver.getTitle().contains("QuickPoll"));
    }

    // TODO: testLoginFlow
    // TODO: testCreatePollFlow
    // TODO: testVoteFlow

    @AfterClass
    public void teardown() { if (driver != null) driver.quit(); }
}
