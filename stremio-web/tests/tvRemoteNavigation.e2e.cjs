const { chromium } = require('playwright');

const describeFocus = async (page) => page.evaluate(() => {
    const element = document.activeElement;
    const rect = element?.getBoundingClientRect();
    return {
        tag: element?.tagName,
        title: element?.getAttribute('title'),
        text: element?.textContent?.trim().slice(0, 80),
        tabIndex: element?.tabIndex,
        rect: rect && { x: rect.x, y: rect.y, width: rect.width, height: rect.height },
    };
});

(async () => {
    const browser = await chromium.launch({ headless: true });
    const page = await browser.newPage({ viewport: { width: 1280, height: 720 } });
    await page.goto('http://127.0.0.1:8080/#/', { waitUntil: 'networkidle' });
    await page.waitForTimeout(2000);

    await page.keyboard.press('Tab');
    await page.keyboard.press('ArrowDown');
    const start = await describeFocus(page);
    const transitions = [];
    const startedAt = Date.now();

    for (let index = 0; index < 60; index += 1) {
        await page.keyboard.press('ArrowLeft');
        transitions.push(await describeFocus(page));
    }

    await page.keyboard.press('ArrowUp');
    const topNavFocus = await describeFocus(page);

    for (let index = 0; index < 120; index += 1) {
        await page.keyboard.press(index % 2 === 0 ? 'ArrowRight' : 'ArrowLeft');
    }

    const elapsedMs = Date.now() - startedAt;
    const navItems = await page.locator("[class*='vertical-nav-bar-container'] [class*='nav-tab-button-container']").evaluateAll((elements) => (
        elements.map((element) => ({ title: element.title, tabIndex: element.tabIndex }))
    ));

    if (navItems.length === 0 || navItems.some(({ tabIndex }) => tabIndex !== 0)) {
        throw new Error(`TV nav items are not remote-focusable: ${JSON.stringify(navItems)}`);
    }
    if (topNavFocus.tabIndex !== 0 || !topNavFocus.title) {
        throw new Error(`ArrowUp did not reach a valid top navigation target: ${JSON.stringify(topNavFocus)}`);
    }
    if (elapsedMs > 3000) {
        throw new Error(`Repeated ArrowLeft navigation was too slow: ${elapsedMs}ms`);
    }

    console.log(JSON.stringify({ start, elapsedMs, finalLeftFocus: transitions.at(-1), topNavFocus, navItems }, null, 2));
    await browser.close();
})().catch((error) => {
    console.error(error);
    process.exitCode = 1;
});
