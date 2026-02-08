import { test, expect } from '@playwright/test';

test('backend is reachable', async ({ request }) => {
    // Retry until backend is reachable (up to 30 seconds)
    await expect(async () => {
        const response = await request.get('http://localhost:8080/health');
        expect(response.status()).toBe(200);
    }).toPass({
        intervals: [1000, 2000, 5000],
        timeout: 30_000,
    });
});
