document.getElementById('searchForm').addEventListener('submit', async (e) => {
    e.preventDefault();
    const restId = document.getElementById('searchRestId').value;
    const date = document.getElementById('searchDate').value;
    const time = document.getElementById('searchTime').value + ':00';
    const party = document.getElementById('searchParty').value;

    const resDiv = document.getElementById('searchResult');
    resDiv.innerHTML = '<span class="spinner-border spinner-border-sm"></span> Checking...';

    try {
        const res = await fetch(`/api/v1/availability?restaurantId=${restId}&date=${date}&time=${time}&partySize=${party}`);
        const data = await res.json();
        if (data.isAvailable) {
            resDiv.innerHTML = `<div class="alert alert-success">✅ Available! Slots: ${data.availableSlots.join(', ')}</div>`;
        } else {
            resDiv.innerHTML = `<div class="alert alert-danger">❌ No table available. Consider joining the waiting list below.</div>`;
        }
    } catch (err) {
        resDiv.innerHTML = `<div class="alert alert-danger">Error: ${err.message}</div>`;
    }
});

document.getElementById('bookForm').addEventListener('submit', async (e) => {
    e.preventDefault();
    const restId = document.getElementById('bookRestId').value;
    const dt = new Date(document.getElementById('bookDateTime').value).toISOString();
    const party = parseInt(document.getElementById('bookParty').value);

    const resDiv = document.getElementById('bookResult');
    resDiv.innerHTML = '<span class="spinner-border spinner-border-sm"></span> Booking...';

    try {
        const res = await fetch('/api/v1/reservations', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                restaurantId: restId,
                partySize: party,
                startTime: dt,
                customerName: 'Alice Customer',
                customerEmail: 'customer1@example.com'
            })
        });
        const data = await res.json();
        if (res.ok) {
            resDiv.innerHTML = `<div class="alert alert-success">🎉 Confirmed! Reservation ID: <code>${data.id}</code><br>Check Mailpit for confirmation email!</div>`;
        } else {
            resDiv.innerHTML = `<div class="alert alert-danger">Failed: ${data.detail || data.title}</div>`;
        }
    } catch (err) {
        resDiv.innerHTML = `<div class="alert alert-danger">Error: ${err.message}</div>`;
    }
});

document.getElementById('waitForm').addEventListener('submit', async (e) => {
    e.preventDefault();
    const restId = document.getElementById('waitRestId').value;
    const date = document.getElementById('waitDate').value;
    const earliest = document.getElementById('waitEarliest').value + ':00';
    const latest = document.getElementById('waitLatest').value + ':00';

    const resDiv = document.getElementById('waitResult');
    resDiv.innerHTML = '<span class="spinner-border spinner-border-sm"></span> Submitting...';

    try {
        const res = await fetch('/api/v1/waiting-list', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                restaurantId: restId,
                targetDate: date,
                earliestTime: earliest,
                latestTime: latest,
                partySize: 4,
                customerEmail: 'customer1@example.com'
            })
        });
        const data = await res.json();
        if (res.ok) {
            resDiv.innerHTML = `<div class="alert alert-warning">⏳ Placed on FIFO Waiting List! Entry ID: <code>${data.id}</code></div>`;
        } else {
            resDiv.innerHTML = `<div class="alert alert-danger">Failed to join waiting list.</div>`;
        }
    } catch (err) {
        resDiv.innerHTML = `<div class="alert alert-danger">Error: ${err.message}</div>`;
    }
});
