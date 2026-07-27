from pathlib import Path

from playwright.sync_api import sync_playwright


output = Path(__file__).resolve().parents[1] / "dashboard-test.png"
console_errors = []

with sync_playwright() as playwright:
    browser = playwright.chromium.launch(headless=True)
    page = browser.new_page(viewport={"width": 1440, "height": 1000}, device_scale_factor=1)
    page.on("console", lambda message: console_errors.append(message.text) if message.type == "error" else None)
    page.goto("http://localhost:8080/", wait_until="networkidle")
    page.wait_for_function("document.querySelector('#socketStatus').textContent === 'LIVE SOCKET'", timeout=15_000)
    page.wait_for_function("document.querySelector('#indexLevel').textContent !== '--'", timeout=15_000)
    page.wait_for_timeout(1_500)

    assert page.locator("#stockRows tr").count() == 50
    assert page.locator("#sectorRows .sector-row").count() >= 10
    assert page.locator("#coverage").inner_text() == "50/50"
    assert page.locator("#indexChart").evaluate("canvas => canvas.width > 0 && canvas.height > 0")
    assert not console_errors, console_errors

    page.screenshot(path=str(output), full_page=True)
    print({
        "socket": page.locator("#socketStatus").inner_text(),
        "index": page.locator("#indexLevel").inner_text(),
        "coverage": page.locator("#coverage").inner_text(),
        "stocks": page.locator("#stockRows tr").count(),
        "screenshot": str(output),
    })

    mobile = browser.new_page(viewport={"width": 390, "height": 844}, device_scale_factor=1)
    mobile.goto("http://localhost:8080/", wait_until="networkidle")
    mobile.wait_for_function("document.querySelector('#socketStatus').textContent === 'LIVE SOCKET'", timeout=15_000)
    mobile.wait_for_function("document.querySelector('#coverage').textContent === '50/50'", timeout=15_000)
    mobile.screenshot(path=str(output.with_name("dashboard-mobile-test.png")), full_page=True)
    assert mobile.locator("html").evaluate("element => element.scrollWidth <= innerWidth + 1")
    mobile.close()
    browser.close()
