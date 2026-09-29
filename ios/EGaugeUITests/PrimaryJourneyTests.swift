import XCTest

final class PrimaryJourneyTests: XCTestCase {
    func testOfflineNavigationAndReviewStayTruthful() {
        let app = XCUIApplication()
        app.launchEnvironment["EGAUGE_UI_TEST_SESSION"] = UUID().uuidString
        app.launch()

        XCTAssertTrue(app.staticTexts["Swipe between pages. Values are examples."].waitForExistence(timeout: 10))
        XCTAssertTrue(app.buttons["Customize gauge"].exists)
        app.buttons["Customize gauge"].tap()
        XCTAssertTrue(app.buttons["Manage pages"].waitForExistence(timeout: 5))
        app.buttons["Manage pages"].tap()
        XCTAssertTrue(app.staticTexts["Keep at least one page. The order here is the order on the gauge."].exists)
        app.buttons["Back"].tap()
        app.buttons["Review setup"].tap()
        XCTAssertTrue(app.staticTexts["Sending to the gauge is not available in this iOS build. Your preview changes remain here."].exists)
        XCTAssertFalse(app.buttons["Send to gauge"].exists)
    }

    func testVehicleProfileSurvivesAppRestart() {
        let app = XCUIApplication()
        app.launchEnvironment["EGAUGE_UI_TEST_SESSION"] = UUID().uuidString
        app.launch()

        app.tabBars.buttons["Car"].tap()
        app.buttons["Add vehicle"].tap()
        let name = app.alerts.textFields["Vehicle name"]
        XCTAssertTrue(name.waitForExistence(timeout: 5))
        name.tap()
        name.typeText("Weekend Jeep")
        app.alerts.buttons["Add"].tap()
        XCTAssertTrue(app.staticTexts["Weekend Jeep"].waitForExistence(timeout: 5))

        app.terminate()
        app.launch()
        app.tabBars.buttons["Car"].tap()
        XCTAssertTrue(app.staticTexts["Weekend Jeep"].waitForExistence(timeout: 5))
    }
}
