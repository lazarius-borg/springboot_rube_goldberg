/**
 * Customer Dining Portal Application Logic
 * Spring Boot Rube Goldberg Restaurant Platform
 */

// --- Global Application State ---
let keycloak = null;
let currentCustomerId = null;
let currentCustomerProfile = null;
let restaurantsList = [];
let restaurantMap = new Map();
let lastSearchResult = null;
let sseSource = null;
let sseReconnectTimer = null;
let sseReconnectDelay = 1000; // start 1s
let offerCountdownInterval = null;
let pendingCancelReservation = null;
let pendingLeaveWaitlistEntry = null;

// Modal instances
let cancelReservationModal = null;
let leaveWaitlistModal = null;

// --- DOM Element References ---
const authLoadingView = document.getElementById('authLoadingView');
const unauthenticatedView = document.getElementById('unauthenticatedView');
const unauthorizedView = document.getElementById('unauthorizedView');
const portalAppView = document.getElementById('portalAppView');
const keycloakErrorBanner = document.getElementById('keycloakErrorBanner');
const keycloakErrorMsg = document.getElementById('keycloakErrorMsg');
const globalAlertContainer = document.getElementById('globalAlertContainer');

const customerDisplayName = document.getElementById('customerDisplayName');
const customerEmailDisplay = document.getElementById('customerEmailDisplay');
const sseStatusDot = document.getElementById('sseStatusDot');
const sseStatusText = document.getElementById('sseStatusText');

const searchAvailabilityForm = document.getElementById('searchAvailabilityForm');
const searchRestaurantSelect = document.getElementById('searchRestaurantSelect');
const searchDateInput = document.getElementById('searchDateInput');
const searchTimeInput = document.getElementById('searchTimeInput');
const searchPartyInput = document.getElementById('searchPartyInput');
const searchResultArea = document.getElementById('searchResultArea');
const directBookingCard = document.getElementById('directBookingCard');
const bookingSummaryText = document.getElementById('bookingSummaryText');
const instantBookBtn = document.getElementById('instantBookBtn');

const waitingListOptInCard = document.getElementById('waitingListOptInCard');
const optInWaitlistForm = document.getElementById('optInWaitlistForm');
const waitEarliestInput = document.getElementById('waitEarliestInput');
const waitLatestInput = document.getElementById('waitLatestInput');

const reservationsLoading = document.getElementById('reservationsLoading');
const noReservationsPlaceholder = document.getElementById('noReservationsPlaceholder');
const reservationsListContainer = document.getElementById('reservationsListContainer');

const waitlistLoading = document.getElementById('waitlistLoading');
const noWaitlistPlaceholder = document.getElementById('noWaitlistPlaceholder');
const waitlistContainer = document.getElementById('waitlistContainer');

const liveOfferSection = document.getElementById('liveOfferSection');
const offerRestaurantName = document.getElementById('offerRestaurantName');
const offerTimeText = document.getElementById('offerTimeText');
const offerCountdown = document.getElementById('offerCountdown');
const acceptOfferBtn = document.getElementById('acceptOfferBtn');

// --- 1. Keycloak OIDC Initialization & Auth Gate ---
function resolveKeycloakUrl() {
    if (window.KEYCLOAK_URL) return window.KEYCLOAK_URL;
    const origin = window.location.origin;
    if (origin.includes(':8080')) {
        return origin.replace(':8080', ':8081');
    }
    return 'http://localhost:8081';
}

function showAuthLoading() {
    if (authLoadingView) {
        authLoadingView.style.display = 'flex';
    }
}

function hideAuthLoading() {
    if (authLoadingView) {
        authLoadingView.style.display = 'none';
    }
}

function initKeycloak() {
    showAuthLoading();
    unauthenticatedView.style.display = 'none';
    unauthorizedView.style.display = 'none';
    portalAppView.style.display = 'none';
    keycloakErrorBanner.classList.add('d-none');

    const keycloakUrl = resolveKeycloakUrl();

    if (typeof Keycloak === 'undefined') {
        console.error('Keycloak JS library is not loaded');
        hideAuthLoading();
        unauthenticatedView.style.display = 'block';
        keycloakErrorMsg.innerText = 'Keycloak authentication library failed to load.';
        keycloakErrorBanner.classList.remove('d-none');
        return;
    }

    try {
        keycloak = new Keycloak({
            url: keycloakUrl,
            realm: 'rube-goldberg',
            clientId: 'rube-goldberg-app'
        });

        keycloak.init({
            onLoad: 'check-sso',
            pkceMethod: 'S256',
            checkLoginIframe: false
        }).then(authenticated => {
            hideAuthLoading();
            if (!authenticated) {
                showUnauthenticated();
            } else {
                handleAuthenticatedUser();
            }
        }).catch(err => {
            console.error('Keycloak initialization failed:', err);
            hideAuthLoading();
            unauthenticatedView.style.display = 'block';
            keycloakErrorMsg.innerText = `Authentication service unavailable at ${keycloakUrl}. Please ensure Keycloak is running.`;
            keycloakErrorBanner.classList.remove('d-none');
        });
    } catch (err) {
        console.error('Error instantiating Keycloak client:', err);
        hideAuthLoading();
        unauthenticatedView.style.display = 'block';
        keycloakErrorMsg.innerText = `Authentication initialization error: ${err.message}`;
        keycloakErrorBanner.classList.remove('d-none');
    }
}

function showUnauthenticated() {
    hideAuthLoading();
    unauthenticatedView.style.display = 'block';
    unauthorizedView.style.display = 'none';
    portalAppView.style.display = 'none';
}

async function handleAuthenticatedUser() {
    hideAuthLoading();
    const roles = keycloak.tokenParsed?.realm_access?.roles || [];
    const isCustomerOrAdmin = roles.includes('CUSTOMER') || roles.includes('ROLE_CUSTOMER') || roles.includes('ADMIN');

    if (!isCustomerOrAdmin) {
        unauthenticatedView.style.display = 'none';
        portalAppView.style.display = 'none';
        unauthorizedView.style.display = 'block';

        document.getElementById('unauthUsername').innerText = keycloak.tokenParsed?.preferred_username || 'Unknown';
        document.getElementById('unauthEmail').innerText = keycloak.tokenParsed?.email || 'N/A';
        document.getElementById('unauthRoles').innerText = roles.join(', ') || 'None';
        return;
    }

    // Authorized Customer / Admin
    unauthenticatedView.style.display = 'none';
    unauthorizedView.style.display = 'none';
    portalAppView.style.display = 'block';

    const givenName = keycloak.tokenParsed?.given_name || keycloak.tokenParsed?.preferred_username || 'Customer';
    const familyName = keycloak.tokenParsed?.family_name || '';
    const email = keycloak.tokenParsed?.email || 'customer@example.com';
    const fullName = `${givenName} ${familyName}`.trim();

    customerDisplayName.innerText = fullName;
    customerEmailDisplay.innerText = email;

    // Load or create customer profile from customer-service
    await syncCustomerProfile(fullName, email);

    // Initialize portal data
    await loadRestaurants();
    await loadUpcomingReservations();
    await loadActiveWaitlist();

    // Start SSE stream for real-time notifications
    initSseStream();
}

// --- 2. Authenticated Fetch Wrapper ---
async function authFetch(url, options = {}) {
    if (!keycloak || !keycloak.authenticated) {
        showUnauthenticated();
        throw new Error('Not authenticated');
    }

    try {
        // Refresh token if expiring in less than 10 seconds
        await keycloak.updateToken(10);
    } catch (err) {
        console.warn('Token refresh failed, redirecting to login:', err);
        keycloak.login();
        throw err;
    }

    options.headers = options.headers || {};
    options.headers['Authorization'] = `Bearer ${keycloak.token}`;
    if (options.body && typeof options.body === 'string' && !options.headers['Content-Type']) {
        options.headers['Content-Type'] = 'application/json';
    }

    const res = await fetch(url, options);
    if (res.status === 401) {
        keycloak.login();
        throw new Error('Session expired');
    }
    return res;
}

// --- 3. Customer Profile Sync ---
async function syncCustomerProfile(name, email) {
    try {
        const res = await authFetch('/api/v1/customers/me');
        if (res.ok) {
            currentCustomerProfile = await res.json();
            currentCustomerId = currentCustomerProfile.id;
            console.log('Synchronized customer profile id:', currentCustomerId);
        }
    } catch (err) {
        console.warn('Could not sync customer profile, fallback to JWT subject:', err);
    }

    if (!currentCustomerId) {
        // Fallback: If JWT subject is a valid UUID, use it; otherwise generate/use a stable customer UUID
        const sub = keycloak.tokenParsed?.sub;
        currentCustomerId = sub || 'c7128e4e-0a56-43b8-89c5-7f2834789b12';
    }
}

// --- 4. Load Restaurants Catalog ---
async function loadRestaurants() {
    try {
        const res = await authFetch('/api/v1/restaurants');
        if (!res.ok) throw new Error('Failed to load restaurants');
        const data = await res.json();
        restaurantsList = data.content || (Array.isArray(data) ? data : []);
        restaurantMap.clear();

        searchRestaurantSelect.innerHTML = '<option value="" disabled selected>Select a restaurant</option>';
        restaurantsList.forEach(r => {
            restaurantMap.set(r.id, r);
            const opt = document.createElement('option');
            opt.value = r.id;
            opt.textContent = `${r.name} (${r.cuisine || 'Dining'})`;
            searchRestaurantSelect.appendChild(opt);
        });

        if (restaurantsList.length > 0) {
            searchRestaurantSelect.selectedIndex = 1; // Default to first available restaurant
            await updateDateTimeConstraints();
        }
    } catch (err) {
        console.error('Error loading restaurants:', err);
        searchRestaurantSelect.innerHTML = '<option value="" disabled>Error loading restaurants</option>';
    }
}

// --- Helper Functions ---
function escapeHtml(str) {
    if (!str) return '';
    return String(str)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#039;');
}

function getSelectedRestaurant() {
    const restId = searchRestaurantSelect.value;
    return restaurantMap.get(restId) || null;
}

function getRestaurantTimezone(restaurant) {
    return (restaurant && restaurant.timezone) ? restaurant.timezone : 'Europe/Amsterdam';
}

function getMinBookingAdvanceMinutes(restaurant) {
    return (restaurant && restaurant.minBookingAdvanceMinutes != null) ? restaurant.minBookingAdvanceMinutes : 30;
}

function getNowInTimezone(tz) {
    try {
        const now = new Date();
        const dtfDate = new Intl.DateTimeFormat('en-CA', { timeZone: tz, year: 'numeric', month: '2-digit', day: '2-digit' });
        const dateStr = dtfDate.format(now);
        const dtfTime = new Intl.DateTimeFormat('en-GB', { timeZone: tz, hour: '2-digit', minute: '2-digit', hour12: false });
        const [hStr, mStr] = dtfTime.format(now).split(':');
        return {
            dateStr,
            hours: parseInt(hStr, 10),
            minutes: parseInt(mStr, 10)
        };
    } catch (e) {
        const now = new Date();
        return {
            dateStr: now.toISOString().split('T')[0],
            hours: now.getHours(),
            minutes: now.getMinutes()
        };
    }
}

function getMinBookingThreshold(restaurant) {
    const tz = getRestaurantTimezone(restaurant);
    const advanceMinutes = getMinBookingAdvanceMinutes(restaurant);
    const nowInfo = getNowInTimezone(tz);

    const totalMinutes = nowInfo.hours * 60 + nowInfo.minutes + advanceMinutes;
    const targetHours = Math.floor(totalMinutes / 60);
    const targetMins = totalMinutes % 60;
    const pad = n => String(n).padStart(2, '0');

    return {
        todayStr: nowInfo.dateStr,
        advanceMinutes,
        isNextDay: targetHours >= 24,
        minTimeStr: `${pad(targetHours % 24)}:${pad(targetMins)}`
    };
}

const restaurantHoursMap = new Map();

async function getRestaurantHours(restaurantId) {
    if (!restaurantId) return [];
    if (restaurantHoursMap.has(restaurantId)) {
        return restaurantHoursMap.get(restaurantId);
    }
    try {
        const res = await authFetch(`/api/v1/restaurants/${restaurantId}/opening-hours`);
        if (res.ok) {
            const data = await res.json();
            const list = Array.isArray(data) ? data : [];
            restaurantHoursMap.set(restaurantId, list);
            return list;
        }
    } catch (e) {
        console.warn('Failed to load opening hours for restaurant', restaurantId, e);
    }
    return [];
}

function getScheduleForDate(hoursList, dateStr) {
    if (!hoursList || hoursList.length === 0 || !dateStr) return null;
    for (const h of hoursList) {
        if (h.specificDate === dateStr) {
            return h;
        }
    }
    const parts = dateStr.split('-').map(Number);
    if (parts.length === 3) {
        const dt = new Date(parts[0], parts[1] - 1, parts[2]);
        const jsDay = dt.getDay();
        const dow = jsDay === 0 ? 7 : jsDay;
        for (const h of hoursList) {
            if (h.dayOfWeek === dow) {
                return h;
            }
        }
    }
    return null;
}

async function updateDateTimeConstraints() {
    const r = getSelectedRestaurant();
    const threshold = getMinBookingThreshold(r);
    const searchTimeHelp = document.getElementById('searchTimeHelp');
    const checkAvailabilityBtn = document.getElementById('checkAvailabilityBtn');

    searchDateInput.min = threshold.todayStr;
    const maxDate = new Date();
    maxDate.setDate(maxDate.getDate() + 365);
    searchDateInput.max = maxDate.toISOString().split('T')[0];

    if (!searchDateInput.value || searchDateInput.value < threshold.todayStr) {
        searchDateInput.value = threshold.todayStr;
    }

    const selectedDate = searchDateInput.value;
    const isToday = (selectedDate === threshold.todayStr);

    let schedule = null;
    if (r) {
        const hoursList = await getRestaurantHours(r.id);
        schedule = getScheduleForDate(hoursList, selectedDate);
    }

    const pad = n => String(n).padStart(2, '0');

    if (schedule) {
        const isClosed = (schedule.closed ?? schedule.isClosed ?? false);
        if (isClosed) {
            searchTimeInput.disabled = true;
            if (checkAvailabilityBtn) checkAvailabilityBtn.disabled = true;
            searchTimeInput.removeAttribute('min');
            searchTimeInput.removeAttribute('max');
            if (searchTimeHelp) {
                searchTimeHelp.innerText = `Restaurant is closed on this date (${selectedDate}). Please select another date.`;
                searchTimeHelp.className = 'form-text text-danger';
                searchTimeHelp.style.display = 'block';
            }
            return;
        }

        const openTimeStr = (schedule.openTime || '11:00:00').substring(0, 5);
        const closeTimeStr = (schedule.closeTime || '23:00:00').substring(0, 5);
        const [closeH, closeM] = closeTimeStr.split(':').map(Number);
        const closeMinutes = closeH * 60 + closeM;

        const minDuration = (r && r.minReservationDurationMinutes) || 45;
        const latestSeatingMinutes = closeMinutes - minDuration;
        const latestH = Math.floor(latestSeatingMinutes / 60);
        const latestM = latestSeatingMinutes % 60;
        const latestSeatingStr = `${pad(latestH)}:${pad(latestM)}`;

        let minAllowedTime = openTimeStr;

        if (isToday) {
            if (threshold.isNextDay || threshold.minTimeStr > latestSeatingStr) {
                searchTimeInput.disabled = true;
                if (checkAvailabilityBtn) checkAvailabilityBtn.disabled = true;
                searchTimeInput.removeAttribute('min');
                searchTimeInput.removeAttribute('max');
                if (searchTimeHelp) {
                    searchTimeHelp.innerText = `No more seating slots available today (operating hours: ${openTimeStr} – ${closeTimeStr}, latest seating: ${latestSeatingStr} with ${threshold.advanceMinutes}m advance notice required).`;
                    searchTimeHelp.className = 'form-text text-danger';
                    searchTimeHelp.style.display = 'block';
                }
                return;
            }
            if (threshold.minTimeStr > openTimeStr) {
                minAllowedTime = threshold.minTimeStr;
            }
        }

        searchTimeInput.disabled = false;
        if (checkAvailabilityBtn) checkAvailabilityBtn.disabled = false;

        searchTimeInput.min = minAllowedTime;
        searchTimeInput.max = latestSeatingStr;

        if (!searchTimeInput.value || searchTimeInput.value < minAllowedTime) {
            searchTimeInput.value = minAllowedTime;
        } else if (searchTimeInput.value > latestSeatingStr) {
            searchTimeInput.value = latestSeatingStr;
        }

        if (searchTimeHelp) {
            searchTimeHelp.className = 'form-text text-muted';
            searchTimeHelp.innerText = `Operating hours: ${openTimeStr} – ${closeTimeStr} (Latest seating: ${latestSeatingStr} for min ${minDuration}m dining; ${threshold.advanceMinutes}m notice required)`;
            searchTimeHelp.style.display = 'block';
        }
    } else {
        searchTimeInput.disabled = false;
        if (checkAvailabilityBtn) checkAvailabilityBtn.disabled = false;

        if (isToday) {
            if (threshold.isNextDay) {
                searchTimeInput.min = '23:59';
                if (searchTimeHelp) {
                    searchTimeHelp.className = 'form-text text-danger';
                    searchTimeHelp.innerText = `No more seating slots available today (${threshold.advanceMinutes}m advance notice required).`;
                    searchTimeHelp.style.display = 'block';
                }
            } else {
                searchTimeInput.min = threshold.minTimeStr;
                searchTimeInput.removeAttribute('max');
                if (!searchTimeInput.value || searchTimeInput.value < threshold.minTimeStr) {
                    searchTimeInput.value = threshold.minTimeStr;
                }
                if (searchTimeHelp) {
                    searchTimeHelp.className = 'form-text text-muted';
                    searchTimeHelp.innerText = `Min lead time: ${threshold.advanceMinutes}m (earliest today: ${threshold.minTimeStr})`;
                    searchTimeHelp.style.display = 'block';
                }
            }
        } else {
            searchTimeInput.removeAttribute('min');
            searchTimeInput.removeAttribute('max');
            if (searchTimeHelp) {
                searchTimeHelp.style.display = 'none';
            }
        }
    }
}

// --- 5. Table Availability Search & Direct Booking Flow ---
function setupDateInputConstraints() {
    searchDateInput.addEventListener('change', () => updateDateTimeConstraints());
    searchRestaurantSelect.addEventListener('change', () => updateDateTimeConstraints());
    updateDateTimeConstraints();
}

searchAvailabilityForm.addEventListener('submit', async (e) => {
    e.preventDefault();
    const restId = searchRestaurantSelect.value;
    const date = searchDateInput.value;
    const time = searchTimeInput.value;
    const party = parseInt(searchPartyInput.value, 10);

    if (!restId || !date || !time) return;

    const r = restaurantMap.get(restId);
    const threshold = getMinBookingThreshold(r);

    if (date < threshold.todayStr) {
        searchResultArea.style.display = 'block';
        searchResultArea.innerHTML = `
            <div class="alert alert-danger mb-0">
                <i class="bi bi-exclamation-octagon-fill me-2"></i>
                <strong>Invalid Date:</strong> Dining date cannot be in the past.
            </div>
        `;
        directBookingCard.style.display = 'none';
        waitingListOptInCard.style.display = 'none';
        return;
    }

    if (date === threshold.todayStr) {
        if (threshold.isNextDay || time < threshold.minTimeStr) {
            searchResultArea.style.display = 'block';
            searchResultArea.innerHTML = `
                <div class="alert alert-danger mb-0">
                    <i class="bi bi-clock-history me-2"></i>
                    <strong>Invalid Dining Time:</strong> Dining time cannot be in the past or within the minimum lead time of ${threshold.advanceMinutes} minutes (earliest available time today is ${threshold.minTimeStr}).
                </div>
            `;
            directBookingCard.style.display = 'none';
            waitingListOptInCard.style.display = 'none';
            return;
        }
    }

    const hoursList = await getRestaurantHours(restId);
    const schedule = getScheduleForDate(hoursList, date);

    if (schedule) {
        const isClosed = (schedule.closed ?? schedule.isClosed ?? false);
        if (isClosed) {
            searchResultArea.style.display = 'block';
            searchResultArea.innerHTML = `
                <div class="alert alert-secondary mb-0">
                    <i class="bi bi-door-closed-fill me-2"></i>
                    <strong>Restaurant Closed!</strong> This restaurant is closed on ${date}. Please select another date.
                </div>
            `;
            directBookingCard.style.display = 'none';
            waitingListOptInCard.style.display = 'none';
            return;
        }

        const openTimeStr = (schedule.openTime || '11:00:00').substring(0, 5);
        const closeTimeStr = (schedule.closeTime || '23:00:00').substring(0, 5);
        const [closeH, closeM] = closeTimeStr.split(':').map(Number);
        const closeMinutes = closeH * 60 + closeM;
        const minDuration = (r && r.minReservationDurationMinutes) || 45;
        const latestSeatingMinutes = closeMinutes - minDuration;
        const pad = n => String(n).padStart(2, '0');
        const latestSeatingStr = `${pad(Math.floor(latestSeatingMinutes / 60))}:${pad(latestSeatingMinutes % 60)}`;

        if (time < openTimeStr) {
            searchResultArea.style.display = 'block';
            searchResultArea.innerHTML = `
                <div class="alert alert-danger mb-0">
                    <i class="bi bi-clock-history me-2"></i>
                    <strong>Invalid Dining Time:</strong> Seating time (${time}) is before the opening time of ${openTimeStr}.
                </div>
            `;
            directBookingCard.style.display = 'none';
            waitingListOptInCard.style.display = 'none';
            return;
        }

        if (time >= closeTimeStr) {
            searchResultArea.style.display = 'block';
            searchResultArea.innerHTML = `
                <div class="alert alert-danger mb-0">
                    <i class="bi bi-clock-history me-2"></i>
                    <strong>Invalid Dining Time:</strong> Seating time (${time}) is at or after closing time (${closeTimeStr}).
                </div>
            `;
            directBookingCard.style.display = 'none';
            waitingListOptInCard.style.display = 'none';
            return;
        }

        if (time > latestSeatingStr) {
            searchResultArea.style.display = 'block';
            searchResultArea.innerHTML = `
                <div class="alert alert-danger mb-0">
                    <i class="bi bi-clock-history me-2"></i>
                    <strong>Invalid Dining Time:</strong> Seating time (${time}) is too close to closing time (${closeTimeStr}). Latest seating allowed is ${latestSeatingStr} for the minimum dining duration of ${minDuration} minutes.
                </div>
            `;
            directBookingCard.style.display = 'none';
            waitingListOptInCard.style.display = 'none';
            return;
        }
    }

    searchResultArea.style.display = 'block';
    searchResultArea.innerHTML = `
        <div class="d-flex align-items-center gap-2 text-primary">
            <div class="spinner-border spinner-border-sm" role="status"></div>
            <span>Checking real-time table availability...</span>
        </div>
    `;
    directBookingCard.style.display = 'none';
    waitingListOptInCard.style.display = 'none';

    try {
        const res = await authFetch(`/api/v1/availability?restaurantId=${restId}&date=${date}&time=${time}:00&partySize=${party}`);
        
        if (!res.ok) {
            const errData = await res.json().catch(() => ({}));
            const detailMsg = errData.detail || (errData.invalidParams && errData.invalidParams[0]?.reason) || 'Failed to verify availability';
            searchResultArea.innerHTML = `
                <div class="alert alert-danger mb-0">
                    <i class="bi bi-exclamation-octagon-fill me-2"></i>
                    <strong>Validation Error:</strong> ${escapeHtml(detailMsg)}
                </div>
            `;
            directBookingCard.style.display = 'none';
            waitingListOptInCard.style.display = 'none';
            return;
        }

        const data = await res.json();
        lastSearchResult = { ...data, restaurantId: restId, date, time, party };

        if (data.isClosed || data.reason === 'RESTAURANT_CLOSED') {
            searchResultArea.innerHTML = `
                <div class="alert alert-secondary mb-0">
                    <i class="bi bi-door-closed-fill me-2"></i>
                    <strong>Restaurant Closed!</strong> This establishment is closed on ${date}. Please select another date.
                </div>
            `;
            directBookingCard.style.display = 'none';
            waitingListOptInCard.style.display = 'none';
            return;
        }

        if (data.reason === 'EXCEEDS_TOTAL_CAPACITY' || (data.totalRestaurantCapacity > 0 && party > data.totalRestaurantCapacity)) {
            searchResultArea.innerHTML = `
                <div class="alert alert-danger mb-0">
                    <i class="bi bi-people-fill me-2"></i>
                    <strong>Party Size Exceeds Capacity!</strong> This restaurant has a total seating capacity of ${data.totalRestaurantCapacity || 'fewer'} guests. Your requested party of ${party} cannot be accommodated.
                </div>
            `;
            directBookingCard.style.display = 'none';
            waitingListOptInCard.style.display = 'none';
            return;
        }

        if (data.isAvailable) {
            // Immediate availability found
            searchResultArea.innerHTML = `
                <div class="alert alert-success d-flex align-items-center mb-0">
                    <i class="bi bi-check-circle-fill me-2 fs-5"></i>
                    <div>
                        <strong>Tables available!</strong> Seating slots: <code>${(data.availableSlots || [time]).join(', ')}</code>
                    </div>
                </div>
            `;
            bookingSummaryText.innerText = `${r ? r.name : 'Restaurant'} on ${date} at ${time} for ${party} guests`;

            // Check if party exceeds single table/combo capacity -> multi-table notice
            const multiNotice = document.getElementById('multiTableNotice');
            if (multiNotice) {
                if (data.maxTableCapacity > 0 && party > data.maxTableCapacity) {
                    multiNotice.style.display = 'block';
                } else {
                    multiNotice.style.display = 'none';
                }
            }

            // Populate duration selector in 15-min increments capped at closing time
            const durationSelect = document.getElementById('bookingDurationSelect');
            const durationHelp = document.getElementById('durationLimitsHelp');
            const minDur = (r && r.minReservationDurationMinutes) || 45;
            const defaultDur = (r && r.defaultReservationDurationMinutes) || 90;
            const maxDur = (r && r.maxReservationDurationMinutes) || 180;

            const [seatingH, seatingM] = time.split(':').map(Number);
            const seatingMinutes = seatingH * 60 + seatingM;
            let maxAllowedForSlot = maxDur;
            if (schedule && schedule.closeTime) {
                const [cH, cM] = schedule.closeTime.substring(0, 5).split(':').map(Number);
                const closeMins = cH * 60 + cM;
                if (closeMins > seatingMinutes) {
                    maxAllowedForSlot = Math.min(maxDur, closeMins - seatingMinutes);
                }
            }

            if (durationHelp) {
                durationHelp.innerText = `(Min: ${minDur} min, Default: ${defaultDur} min, Max: ${maxAllowedForSlot} min)`;
            }
            if (durationSelect) {
                durationSelect.innerHTML = '';
                for (let d = minDur; d <= maxAllowedForSlot; d += 15) {
                    const opt = document.createElement('option');
                    opt.value = d;
                    opt.textContent = `${d} minutes${d === defaultDur ? ' (Default)' : ''}${d === maxAllowedForSlot ? ' (Max before close)' : ''}`;
                    if (d === defaultDur || (defaultDur > maxAllowedForSlot && d === maxAllowedForSlot)) opt.selected = true;
                    durationSelect.appendChild(opt);
                }
                if (durationSelect.options.length === 0) {
                    const opt = document.createElement('option');
                    opt.value = minDur;
                    opt.textContent = `${minDur} minutes`;
                    opt.selected = true;
                    durationSelect.appendChild(opt);
                }
            }

            directBookingCard.style.display = 'block';
            waitingListOptInCard.style.display = 'none';
        } else {
            // Unavailable slot -> Prompt conditional waiting list
            searchResultArea.innerHTML = `
                <div class="alert alert-warning mb-0">
                    <i class="bi bi-exclamation-triangle-fill me-2"></i>
                    <strong>Fully Booked!</strong> No direct tables available for ${party} guests at ${time} on ${date}.
                </div>
            `;

            // Auto-populate ±1 hour window ensuring it is NOT in the past and within operating hours
            const [hours, minutes] = time.split(':').map(Number);
            const earliestH = Math.max(0, hours - 1);
            const latestH = Math.min(23, hours + 1);
            const pad = (n) => String(n).padStart(2, '0');

            let earliestStr = `${pad(earliestH)}:${pad(minutes)}`;
            let latestStr = `${pad(latestH)}:${pad(minutes)}`;

            if (date === threshold.todayStr) {
                if (earliestStr < threshold.minTimeStr) {
                    earliestStr = threshold.minTimeStr;
                }
                if (latestStr <= earliestStr) {
                    const [eh, em] = earliestStr.split(':').map(Number);
                    const lh = Math.min(23, eh + 1);
                    latestStr = `${pad(lh)}:${pad(em)}`;
                }
            }

            if (schedule) {
                const openTimeStr = (schedule.openTime || '11:00:00').substring(0, 5);
                const closeTimeStr = (schedule.closeTime || '23:00:00').substring(0, 5);
                const [closeH, closeM] = closeTimeStr.split(':').map(Number);
                const closeMinutes = closeH * 60 + closeM;
                const minDuration = (r && r.minReservationDurationMinutes) || 45;
                const latestSeatingMinutes = closeMinutes - minDuration;
                const latestSeatingStr = `${pad(Math.floor(latestSeatingMinutes / 60))}:${pad(latestSeatingMinutes % 60)}`;

                if (earliestStr < openTimeStr) {
                    earliestStr = openTimeStr;
                }
                if (latestStr > latestSeatingStr) {
                    latestStr = latestSeatingStr;
                }
                if (latestStr < earliestStr) {
                    latestStr = earliestStr;
                }
            }

            waitEarliestInput.value = earliestStr;
            waitLatestInput.value = latestStr;

            directBookingCard.style.display = 'none';
            waitingListOptInCard.style.display = 'block';
        }
    } catch (err) {
        searchResultArea.innerHTML = `
            <div class="alert alert-danger mb-0">
                <i class="bi bi-exclamation-octagon-fill me-2"></i> Failed to verify availability: ${escapeHtml(err.message)}
            </div>
        `;
        directBookingCard.style.display = 'none';
        waitingListOptInCard.style.display = 'none';
    }
});

// Instant Booking Action
instantBookBtn.addEventListener('click', async () => {
    if (!lastSearchResult) return;
    const { restaurantId, date, time, party, availableTables, combinations } = lastSearchResult;

    instantBookBtn.disabled = true;
    instantBookBtn.innerHTML = '<span class="spinner-border spinner-border-sm me-2"></span>Confirming Booking...';

    const startDateTime = `${date}T${time}:00`;
    const customerName = customerDisplayName.innerText || 'Customer';
    const customerEmail = customerEmailDisplay.innerText || 'customer@example.com';
    const durationSelect = document.getElementById('bookingDurationSelect');
    const durationMinutes = durationSelect ? parseInt(durationSelect.value, 10) : 90;

    try {
        const payload = {
            restaurantId,
            customerId: currentCustomerId,
            customerName,
            customerEmail,
            partySize: party,
            startTime: new Date(startDateTime).toISOString(),
            durationMinutes: durationMinutes || 90,
            cancellationWindowHours: 2,
            availableTables: availableTables && availableTables.length > 0 ? availableTables : undefined,
            combinations: combinations && combinations.length > 0 ? combinations : undefined
        };

        const res = await authFetch('/api/v1/reservations', {
            method: 'POST',
            body: JSON.stringify(payload)
        });

        if (res.ok) {
            const reservation = await res.json();
            showGlobalAlert('success', `🎉 Reservation confirmed! Booking ID: <code>${reservation.id}</code>. Check Mailpit for your confirmation email.`);
            directBookingCard.style.display = 'none';
            searchResultArea.style.display = 'none';
            await loadUpcomingReservations();
        } else {
            const errData = await res.json().catch(() => ({}));
            showGlobalAlert('danger', `Booking failed: ${errData.detail || errData.title || res.statusText}`);
        }
    } catch (err) {
        showGlobalAlert('danger', `Error placing reservation: ${err.message}`);
    } finally {
        instantBookBtn.disabled = false;
        instantBookBtn.innerHTML = '<i class="bi bi-bookmark-check-fill me-1"></i>Confirm Instant Reservation';
    }
});

// Waiting List Opt-In Action
optInWaitlistForm.addEventListener('submit', async (e) => {
    e.preventDefault();
    if (!lastSearchResult) return;
    const { restaurantId, date, party } = lastSearchResult;
    const earliestTime = waitEarliestInput.value + ':00';
    const latestTime = waitLatestInput.value + ':00';
    const customerEmail = customerEmailDisplay.innerText || 'customer@example.com';

    const submitBtn = document.getElementById('joinWaitlistBtn');
    submitBtn.disabled = true;
    submitBtn.innerHTML = '<span class="spinner-border spinner-border-sm me-2"></span>Joining Queue...';

    try {
        const payload = {
            restaurantId,
            customerId: currentCustomerId,
            customerEmail,
            targetDate: date,
            earliestTime,
            latestTime,
            partySize: party
        };

        const res = await authFetch('/api/v1/waiting-list', {
            method: 'POST',
            body: JSON.stringify(payload)
        });

        if (res.ok) {
            const entry = await res.json();
            showGlobalAlert('info', `⏳ You've been placed on the Fair FIFO Waiting List! Entry ID: <code>${entry.id}</code>. We'll alert you immediately if a table opens.`);
            waitingListOptInCard.style.display = 'none';
            searchResultArea.style.display = 'none';
            await loadActiveWaitlist();
        } else {
            const errData = await res.json().catch(() => ({}));
            showGlobalAlert('danger', `Failed to join waiting list: ${errData.detail || errData.title || res.statusText}`);
        }
    } catch (err) {
        showGlobalAlert('danger', `Error joining waiting list: ${err.message}`);
    } finally {
        submitBtn.disabled = false;
        submitBtn.innerHTML = '<i class="bi bi-person-plus-fill me-1"></i>Join FIFO Waiting List';
    }
});

// --- 6. Self-Service Upcoming Reservations ---
async function loadUpcomingReservations() {
    reservationsLoading.style.display = 'block';
    noReservationsPlaceholder.style.display = 'none';
    reservationsListContainer.style.display = 'none';

    try {
        const res = await authFetch(`/api/v1/reservations?customerId=${encodeURIComponent(currentCustomerId)}`);
        if (!res.ok) throw new Error('Failed to fetch reservations');
        const data = await res.json();
        const reservations = data.content || (Array.isArray(data) ? data : []);

        reservationsLoading.style.display = 'none';

        if (reservations.length === 0) {
            noReservationsPlaceholder.style.display = 'block';
            return;
        }

        reservationsListContainer.innerHTML = '';
        reservations.forEach(r => {
            const card = createReservationCard(r);
            reservationsListContainer.appendChild(card);
        });
        reservationsListContainer.style.display = 'flex';
    } catch (err) {
        reservationsLoading.style.display = 'none';
        console.error('Error loading reservations:', err);
    }
}

function createReservationCard(reservation) {
    const card = document.createElement('div');
    card.className = 'card border rounded-3 p-3 bg-white shadow-sm';

    const rest = restaurantMap.get(reservation.restaurantId);
    const restName = rest ? rest.name : 'Restaurant';
    const startDt = new Date(reservation.startTime);
    const dateFormatted = startDt.toLocaleDateString(undefined, { weekday: 'short', month: 'short', day: 'numeric', year: 'numeric' });
    const timeFormatted = startDt.toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' });

    let statusBadgeClass = 'bg-secondary';
    if (reservation.status === 'CONFIRMED') statusBadgeClass = 'bg-success';
    if (reservation.status === 'CANCELLED') statusBadgeClass = 'bg-danger';
    if (reservation.status === 'COMPLETED') statusBadgeClass = 'bg-primary';

    // Allow cancellation only for CONFIRMED status
    const canCancel = reservation.status === 'CONFIRMED';

    card.innerHTML = `
        <div class="d-flex justify-content-between align-items-start mb-2">
            <div>
                <h6 class="fw-bold mb-1 text-dark">${restName}</h6>
                <div class="text-secondary small">
                    <i class="bi bi-calendar3 me-1"></i> ${dateFormatted} at ${timeFormatted}
                </div>
            </div>
            <span class="badge ${statusBadgeClass} status-badge">${reservation.status}</span>
        </div>
        <div class="d-flex justify-content-between align-items-center pt-2 border-top mt-2">
            <div class="small text-muted">
                <i class="bi bi-people-fill me-1"></i> Party of ${reservation.partySize}
            </div>
            ${canCancel ? `
                <button class="btn btn-sm btn-outline-danger cancel-res-btn" data-id="${reservation.id}">
                    <i class="bi bi-x-circle me-1"></i> Cancel Booking
                </button>
            ` : ''}
        </div>
    `;

    if (canCancel) {
        const cancelBtn = card.querySelector('.cancel-res-btn');
        cancelBtn.addEventListener('click', () => {
            openCancelReservationModal(reservation, restName, `${dateFormatted} at ${timeFormatted}`);
        });
    }

    return card;
}

function openCancelReservationModal(reservation, restName, dateTimeStr) {
    pendingCancelReservation = reservation;
    document.getElementById('modalCancelRestaurant').innerText = restName;
    document.getElementById('modalCancelDateTime').innerText = dateTimeStr;
    document.getElementById('modalCancelParty').innerText = `${reservation.partySize} guests`;
    document.getElementById('cancelReasonInput').value = 'Customer cancellation';
    document.getElementById('cancelModalError').classList.add('d-none');

    if (!cancelReservationModal) {
        cancelReservationModal = new bootstrap.Modal(document.getElementById('cancelReservationModal'));
    }
    cancelReservationModal.show();
}

document.getElementById('confirmCancelReservationBtn').addEventListener('click', async () => {
    if (!pendingCancelReservation) return;

    const confirmBtn = document.getElementById('confirmCancelReservationBtn');
    const modalError = document.getElementById('cancelModalError');
    const reason = document.getElementById('cancelReasonInput').value || 'Customer cancellation';

    confirmBtn.disabled = true;
    confirmBtn.innerHTML = '<span class="spinner-border spinner-border-sm me-2"></span>Cancelling...';
    modalError.classList.add('d-none');

    try {
        const res = await authFetch(`/api/v1/reservations/${pendingCancelReservation.id}?reason=${encodeURIComponent(reason)}`, {
            method: 'DELETE'
        });

        if (res.ok) {
            cancelReservationModal.hide();
            showGlobalAlert('success', 'Reservation successfully cancelled. Allocated tables released.');
            await loadUpcomingReservations();
        } else {
            const errData = await res.json().catch(() => ({}));
            modalError.innerText = errData.detail || errData.title || 'Cancellation failed. Cancellation window has passed.';
            modalError.classList.remove('d-none');
        }
    } catch (err) {
        modalError.innerText = `Error cancelling: ${err.message}`;
        modalError.classList.remove('d-none');
    } finally {
        confirmBtn.disabled = false;
        confirmBtn.innerHTML = 'Confirm Cancellation';
    }
});

// --- 7. Active Waiting List Tracking & Leaving ---
async function loadActiveWaitlist() {
    waitlistLoading.style.display = 'block';
    noWaitlistPlaceholder.style.display = 'none';
    waitlistContainer.style.display = 'none';

    try {
        const res = await authFetch(`/api/v1/waiting-list?customerId=${encodeURIComponent(currentCustomerId)}`);
        if (!res.ok) throw new Error('Failed to fetch waitlist');
        const entries = await res.json();

        waitlistLoading.style.display = 'none';

        if (!entries || entries.length === 0) {
            noWaitlistPlaceholder.style.display = 'block';
            return;
        }

        waitlistContainer.innerHTML = '';
        entries.forEach(entry => {
            const card = createWaitlistCard(entry);
            waitlistContainer.appendChild(card);
        });
        waitlistContainer.style.display = 'flex';
    } catch (err) {
        waitlistLoading.style.display = 'none';
        console.error('Error loading waitlist:', err);
    }
}

function createWaitlistCard(entry) {
    const card = document.createElement('div');
    card.className = 'card border rounded-3 p-3 bg-white shadow-sm';

    const rest = restaurantMap.get(entry.restaurantId);
    const restName = rest ? rest.name : 'Restaurant';

    let statusBadgeClass = 'bg-secondary';
    if (entry.status === 'WAITING') statusBadgeClass = 'bg-warning text-dark';
    if (entry.status === 'OFFERED') statusBadgeClass = 'bg-info text-dark';
    if (entry.status === 'CONVERTED') statusBadgeClass = 'bg-success';
    if (entry.status === 'CANCELLED') statusBadgeClass = 'bg-danger';

    const canLeave = entry.status === 'WAITING' || entry.status === 'OFFERED';

    card.innerHTML = `
        <div class="d-flex justify-content-between align-items-start mb-2">
            <div>
                <h6 class="fw-bold mb-1 text-dark">${restName}</h6>
                <div class="text-secondary small">
                    <i class="bi bi-calendar3 me-1"></i> Target Date: ${entry.targetDate}
                </div>
            </div>
            <div class="d-flex align-items-center gap-2">
                <span class="badge bg-light text-dark border"><i class="bi bi-people-fill me-1 text-primary"></i>${entry.partySize} Guests</span>
                <span class="badge ${statusBadgeClass} status-badge">${entry.status}</span>
            </div>
        </div>
        <div class="d-flex justify-content-between align-items-center pt-2 border-top mt-2">
            <div class="small text-muted">
                <i class="bi bi-clock me-1"></i> Window: ${entry.earliestTime} - ${entry.latestTime}
            </div>
            ${canLeave ? `
                <button class="btn btn-sm btn-outline-secondary leave-wait-btn" data-id="${entry.id}">
                    <i class="bi bi-box-arrow-left me-1"></i> Leave Queue
                </button>
            ` : ''}
        </div>
    `;

    if (canLeave) {
        const leaveBtn = card.querySelector('.leave-wait-btn');
        leaveBtn.addEventListener('click', () => {
            openLeaveWaitlistModal(entry, restName, `${entry.earliestTime} - ${entry.latestTime}`);
        });
    }

    return card;
}

function openLeaveWaitlistModal(entry, restName, windowStr) {
    pendingLeaveWaitlistEntry = entry;
    document.getElementById('modalLeaveRestaurant').innerText = restName;
    document.getElementById('modalLeaveDate').innerText = entry.targetDate;
    document.getElementById('modalLeaveWindow').innerText = windowStr;
    document.getElementById('leaveModalError').classList.add('d-none');

    if (!leaveWaitlistModal) {
        leaveWaitlistModal = new bootstrap.Modal(document.getElementById('leaveWaitlistModal'));
    }
    leaveWaitlistModal.show();
}

document.getElementById('confirmLeaveWaitlistBtn').addEventListener('click', async () => {
    if (!pendingLeaveWaitlistEntry) return;

    const confirmBtn = document.getElementById('confirmLeaveWaitlistBtn');
    const modalError = document.getElementById('leaveModalError');

    confirmBtn.disabled = true;
    confirmBtn.innerHTML = '<span class="spinner-border spinner-border-sm me-2"></span>Removing...';
    modalError.classList.add('d-none');

    try {
        const res = await authFetch(`/api/v1/waiting-list/${pendingLeaveWaitlistEntry.id}`, {
            method: 'DELETE'
        });

        if (res.ok) {
            leaveWaitlistModal.hide();
            showGlobalAlert('info', 'You have been removed from the waiting list.');
            await loadActiveWaitlist();
        } else {
            const errData = await res.json().catch(() => ({}));
            modalError.innerText = errData.detail || errData.title || 'Failed to leave waiting list.';
            modalError.classList.remove('d-none');
        }
    } catch (err) {
        modalError.innerText = `Error: ${err.message}`;
        modalError.classList.remove('d-none');
    } finally {
        confirmBtn.disabled = false;
        confirmBtn.innerHTML = 'Leave Queue';
    }
});

// --- 8. Real-Time Server-Sent Events (SSE) & Offer Countdown ---
function setSseStatus(state, message) {
    sseStatusDot.className = 'connection-dot';
    if (state === 'connected') {
        sseStatusDot.classList.add('dot-connected');
        sseStatusText.innerText = 'Live Updates: Active';
    } else if (state === 'connecting') {
        sseStatusDot.classList.add('dot-connecting');
        sseStatusText.innerText = 'Connecting...';
    } else {
        sseStatusDot.classList.add('dot-disconnected');
        sseStatusText.innerText = message || 'Offline';
    }
}

function initSseStream() {
    if (!keycloak || !keycloak.authenticated) return;
    if (sseSource) {
        sseSource.close();
        sseSource = null;
    }

    setSseStatus('connecting');

    // Standard EventSource connection with token and customerId query parameters
    const sseUrl = `/api/v1/notifications/stream?token=${encodeURIComponent(keycloak.token)}&customerId=${encodeURIComponent(currentCustomerId)}`;
    sseSource = new EventSource(sseUrl);

    sseSource.addEventListener('connected', (e) => {
        console.log('SSE Stream established:', e.data);
        setSseStatus('connected');
        sseReconnectDelay = 1000; // Reset backoff delay
    });

    sseSource.addEventListener('ping', () => {
        // Keep-alive heartbeat received
    });

    sseSource.addEventListener('RESERVATION_CONFIRMED', (e) => {
        console.log('Received RESERVATION_CONFIRMED:', e.data);
        showGlobalAlert('success', '🎉 Your reservation has been confirmed!');
        loadUpcomingReservations();
    });

    sseSource.addEventListener('RESERVATION_CANCELLED', (e) => {
        console.log('Received RESERVATION_CANCELLED:', e.data);
        showGlobalAlert('warning', 'A reservation was cancelled.');
        loadUpcomingReservations();
    });

    sseSource.addEventListener('WAITING_LIST_OFFER', (e) => {
        console.log('Received WAITING_LIST_OFFER:', e.data);
        try {
            const offerData = JSON.parse(e.data);
            handleIncomingOffer(offerData);
        } catch (err) {
            console.error('Failed to parse offer payload:', err);
        }
    });

    sseSource.onerror = (err) => {
        console.warn('SSE stream error, reconnecting...', err);
        setSseStatus('disconnected', 'Reconnecting...');
        sseSource.close();
        sseSource = null;

        // Exponential backoff reconnect
        if (sseReconnectTimer) clearTimeout(sseReconnectTimer);
        sseReconnectTimer = setTimeout(() => {
            sseReconnectDelay = Math.min(sseReconnectDelay * 2, 30000);
            initSseStream();
        }, sseReconnectDelay);
    };
}

function handleIncomingOffer(offer) {
    const rest = restaurantMap.get(offer.restaurantId);
    const restName = rest ? rest.name : 'Restaurant';
    const startDt = new Date(offer.offeredStartTime);
    const timeFormatted = startDt.toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' });
    const dateFormatted = startDt.toLocaleDateString(undefined, { weekday: 'short', month: 'short', day: 'numeric' });

    offerRestaurantName.innerText = restName;
    offerTimeText.innerText = `${dateFormatted} at ${timeFormatted}`;
    liveOfferSection.style.display = 'block';

    // Start 15-minute countdown
    const expiresAtMs = new Date(offer.expiresAt).getTime();
    startOfferCountdown(expiresAtMs, offer.offerId);

    // Bind accept offer action
    acceptOfferBtn.disabled = false;
    acceptOfferBtn.innerHTML = '<i class="bi bi-check-circle-fill me-2"></i> Accept Offer Now';
    acceptOfferBtn.onclick = () => acceptOffer(offer.offerId);

    // Refresh waitlist cards
    loadActiveWaitlist();
}

function startOfferCountdown(expiresAtMs, offerId) {
    if (offerCountdownInterval) clearInterval(offerCountdownInterval);

    function update() {
        const now = Date.now();
        const diffMs = expiresAtMs - now;

        if (diffMs <= 0) {
            clearInterval(offerCountdownInterval);
            offerCountdown.innerText = '00:00';
            offerCountdown.classList.remove('timer-urgent');
            acceptOfferBtn.disabled = true;
            acceptOfferBtn.innerText = 'Offer Expired';
            showGlobalAlert('danger', 'Waiting list offer has expired and has been offered to the next guest in line.');
            setTimeout(() => {
                liveOfferSection.style.display = 'none';
            }, 5000);
            return;
        }

        const totalSeconds = Math.floor(diffMs / 1000);
        const mins = Math.floor(totalSeconds / 60);
        const secs = totalSeconds % 60;
        const pad = (n) => String(n).padStart(2, '0');

        offerCountdown.innerText = `${pad(mins)}:${pad(secs)}`;

        // Urgent warning styling when under 2 minutes remaining
        if (totalSeconds < 120) {
            offerCountdown.classList.add('timer-urgent');
        } else {
            offerCountdown.classList.remove('timer-urgent');
        }
    }

    update();
    offerCountdownInterval = setInterval(update, 1000);
}

async function acceptOffer(offerId) {
    acceptOfferBtn.disabled = true;
    acceptOfferBtn.innerHTML = '<span class="spinner-border spinner-border-sm me-2"></span>Claiming Table...';

    try {
        const res = await authFetch(`/api/v1/waiting-list/offers/${offerId}/accept`, {
            method: 'POST'
        });

        if (res.ok) {
            clearInterval(offerCountdownInterval);
            liveOfferSection.style.display = 'none';
            showGlobalAlert('success', '🎉 Table Offer Accepted! Your guaranteed reservation is confirmed. Enjoy your dining!');
            await loadUpcomingReservations();
            await loadActiveWaitlist();
        } else if (res.status === 410) {
            clearInterval(offerCountdownInterval);
            liveOfferSection.style.display = 'none';
            showGlobalAlert('danger', 'This table offer has expired or has already been claimed.');
            await loadActiveWaitlist();
        } else {
            const errData = await res.json().catch(() => ({}));
            showGlobalAlert('danger', `Failed to accept offer: ${errData.detail || errData.title || res.statusText}`);
            acceptOfferBtn.disabled = false;
            acceptOfferBtn.innerHTML = '<i class="bi bi-check-circle-fill me-2"></i> Accept Offer Now';
        }
    } catch (err) {
        showGlobalAlert('danger', `Error accepting offer: ${err.message}`);
        acceptOfferBtn.disabled = false;
        acceptOfferBtn.innerHTML = '<i class="bi bi-check-circle-fill me-2"></i> Accept Offer Now';
    }
}

// --- 9. Global Notifications Helper ---
function showGlobalAlert(type, messageHtml) {
    globalAlertContainer.style.display = 'block';
    globalAlertContainer.innerHTML = `
        <div class="alert alert-${type} alert-dismissible fade show shadow-sm" role="alert">
            ${messageHtml}
            <button type="button" class="btn-close" data-bs-dismiss="alert" aria-label="Close"></button>
        </div>
    `;
    window.scrollTo({ top: 0, behavior: 'smooth' });
}

// --- 10. Event Listeners ---
document.getElementById('loginBtn')?.addEventListener('click', () => {
    if (keycloak) keycloak.login();
});

document.getElementById('logoutBtn')?.addEventListener('click', () => {
    if (keycloak) keycloak.logout();
});

document.getElementById('unauthLogoutBtn')?.addEventListener('click', () => {
    if (keycloak) keycloak.logout();
});

document.getElementById('manualRefreshBtn')?.addEventListener('click', async () => {
    await loadUpcomingReservations();
    await loadActiveWaitlist();
    if (!sseSource || sseSource.readyState === EventSource.CLOSED) {
        initSseStream();
    }
});

document.getElementById('refreshReservationsBtn')?.addEventListener('click', loadUpcomingReservations);
document.getElementById('refreshWaitlistBtn')?.addEventListener('click', loadActiveWaitlist);

// --- Initialization ---
document.addEventListener('DOMContentLoaded', () => {
    setupDateInputConstraints();
    initKeycloak();
});
