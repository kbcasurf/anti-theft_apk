/**
 * Authentication Middleware
 * Validates Bearer tokens from device requests
 */

/**
 * Validates the Authorization header and extracts the token
 */
function authMiddleware(req, res, next) {
    const authHeader = req.headers['authorization'];

    if (!authHeader) {
        console.warn('[Auth] Missing authorization header');
        return res.status(401).json({
            error: 'Unauthorized',
            message: 'Missing authorization header'
        });
    }

    // Extract token from "Bearer <token>" format
    const parts = authHeader.split(' ');

    if (parts.length !== 2 || parts[0] !== 'Bearer') {
        console.warn('[Auth] Invalid authorization format');
        return res.status(401).json({
            error: 'Unauthorized',
            message: 'Invalid authorization format. Expected: Bearer <token>'
        });
    }

    const token = parts[1];

    if (!token || token.trim() === '') {
        console.warn('[Auth] Empty token provided');
        return res.status(401).json({
            error: 'Unauthorized',
            message: 'Empty token provided'
        });
    }

    // Store token in request for use by route handlers
    req.authToken = token;

    // For personal use, we accept any valid token format
    // In production, you would validate against stored tokens or use JWT
    console.log('[Auth] ✓ Token validated:', token.substring(0, 8) + '...');

    next();
}

module.exports = authMiddleware;
