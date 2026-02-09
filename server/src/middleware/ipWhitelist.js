/**
 * IP Whitelist Middleware
 * Restricts access to the server based on IP addresses
 */

const config = require('../config/config');

/**
 * Checks if an IP address matches a whitelist entry
 * Supports individual IPs and CIDR notation (e.g., 192.168.1.0/24)
 */
function isIPWhitelisted(clientIP, whitelistEntry) {
    // Direct match
    if (clientIP === whitelistEntry || clientIP.includes(whitelistEntry)) {
        return true;
    }

    // Check CIDR notation
    if (whitelistEntry.includes('/')) {
        return matchesCIDR(clientIP, whitelistEntry);
    }

    return false;
}

/**
 * Checks if an IP matches a CIDR range
 * Basic implementation for IPv4
 */
function matchesCIDR(ip, cidr) {
    // Remove IPv6 prefix if present
    if (ip.startsWith('::ffff:')) {
        ip = ip.substring(7);
    }

    const [range, bits] = cidr.split('/');
    const mask = ~(2 ** (32 - parseInt(bits)) - 1);

    const ipInt = ipToInt(ip);
    const rangeInt = ipToInt(range);

    return (ipInt & mask) === (rangeInt & mask);
}

/**
 * Converts IPv4 address to integer
 */
function ipToInt(ip) {
    return ip.split('.').reduce((acc, octet) => (acc << 8) + parseInt(octet), 0) >>> 0;
}

/**
 * Express middleware function
 */
function ipWhitelist(req, res, next) {
    // Get client IP from various sources
    const clientIP = req.ip ||
                     req.connection.remoteAddress ||
                     req.socket.remoteAddress ||
                     (req.connection.socket ? req.connection.socket.remoteAddress : null);

    console.log(`[IP Whitelist] Request from: ${clientIP}`);

    // Check against whitelist
    const isWhitelisted = config.whitelistedIPs.some(whitelistEntry =>
        isIPWhitelisted(clientIP, whitelistEntry)
    );

    if (!isWhitelisted) {
        console.warn(`[IP Whitelist] ⛔ Blocked IP: ${clientIP}`);
        return res.status(403).json({
            error: 'Forbidden',
            message: 'IP address not authorized',
            ip: clientIP
        });
    }

    console.log(`[IP Whitelist] ✓ Allowed IP: ${clientIP}`);
    next();
}

module.exports = ipWhitelist;
