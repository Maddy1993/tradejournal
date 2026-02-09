import { test, expect } from '@playwright/test';

test('Alpaca Paper Account Integration', async ({ page, request }) => {
    // 1. Wait for backend to be ready
    await expect(async () => {
        const response = await request.get('http://localhost:8080/health');
        expect(response.status()).toBe(200);
    }).toPass({
        intervals: [1000, 2000, 5000],
        timeout: 60_000, // Wait longer for startup
    });

    // 2. Go to home page
    await page.goto('http://localhost:3000');

    // 3. Login
    const emailInput = page.locator('input[type="email"]');
    if (await emailInput.isVisible()) {
        await emailInput.fill('test@mail.com');
        await page.getByRole('button', { name: /Get Started|Login/i }).click();
    }

    // 4. Wait for Dashboard to load (Verify auto-healing)
    // Check for "Total Equity" or "Holdings" or similar text
    // The previous logs failed around "Failed to fetch performance" -> 401
    // If auto-healing works, this should load.
    await expect(page.getByText('Total Equity')).toBeVisible({ timeout: 15_000 });
    await expect(page.getByText('Unrealized P&L')).toBeVisible();

    // 5. Navigate to Settings to simulate connection/ensure connected
    // Assuming settings is accessible from menu
    // The previous logs showed a "Settings" link
    await page.getByRole('link', { name: /Settings/i }).click();

    // Check if connected. If we see "SnapTrade" or "Alpaca" in the list.
    // Or check for "Connect Broker" button.
    const connectButton = page.getByRole('button', { name: /Connect Broker/i });
    if (await connectButton.isVisible()) {
        // If visible, maybe click it
        await connectButton.click();
        // But this might open a popup/redirect which is complex for simple test
        // Assuming user is already connected OR auto-healing handles the missing connection link logic too?
        // No, auto-healing handles the invalid secret. 
        // If `test@mail.com` is connected, it should show established connection.
    }

    // 6. Go back to dashboard/Trade History
    await page.goto('http://localhost:3000/trades'); // Assuming URL structure or navigate via menu

    // Wait for trade history table or empty state
    // If table is not visible, we might need to sync first
    const table = page.getByRole('table');
    const emptyState = page.getByText(/No Trades Found/i);

    await expect(table.or(emptyState)).toBeVisible();

    if (await emptyState.isVisible()) {
        const syncButton = page.getByRole('button', { name: /Sync|Refresh/i });
        if (await syncButton.isVisible()) {
            await syncButton.click();
            // Wait for sync to complete (table should appear)
            await expect(table).toBeVisible({ timeout: 10000 });
        }
    } else {
        await expect(table).toBeVisible();
    }

    // Check for populated rows
    const rows = page.locator('tbody tr');

    // Verify rows exist
    await expect(rows).not.toHaveCount(0);

    // Check "Realized P&L" column header
    await expect(page.getByText('Realized P&L')).toBeVisible();

    // Check first row for realized P&L value (non-empty)
    const realizedPlCell = rows.first().locator('td:has-text("$")').or(rows.first().locator('td:has-text("-")')); // Assuming currency or just number
    // More robust check: finding the specific column index for Realized P&L?
    // For now, just checking row content.
    await expect(rows.first()).toContainText('Realized P&L', { timeout: 1000 }).catch(() => { }); // might not contain the text itself

    // Check consistency
    const headers = page.locator('thead th');
    const realizedPlIndex = await headers.allTextContents().then(texts => texts.findIndex(t => t.includes('Realized P&L')));

    if (realizedPlIndex !== -1) {
        const cell = rows.first().locator(`td:nth-child(${realizedPlIndex + 1})`);
        await expect(cell).not.toBeEmpty();
    }
});
