package com.example.backend

/**
 * Full Architecture Specs & Source Code Templates for Backend Architecture (Node.js/PostgreSQL/PostGIS)
 * & REST API Client Integration.
 */
object BackendArchitecture {

    const val EXPRESS_POSTGIS_BACKEND_CODE = """
// ============================================================================
// GPS SETTER PRO - BACKEND API & GEOSPATIAL SERVER
// Tech Stack: Node.js, Express, PostgreSQL + PostGIS, JWT, WebSockets
// ============================================================================

const express = require('express');
const { Pool } = require('pg');
const jwt = require('jsonwebtoken');
const bcrypt = require('bcrypt');
const WebSocket = require('ws');

const app = express();
app.use(express.json());

// PostgreSQL + PostGIS Connection Pool
const db = new Pool({
  user: process.env.DB_USER || 'postgres',
  host: process.env.DB_HOST || 'localhost',
  database: process.env.DB_NAME || 'gps_setter_db',
  password: process.env.DB_PASSWORD || 'secret',
  port: 5432,
});

const JWT_SECRET = process.env.JWT_SECRET || 'super_secret_key_gps_setter_2026';

// Initialize PostGIS Table Schema
async function initDb() {
  await db.query(`
    CREATE EXTENSION IF NOT EXISTS postgis;

    CREATE TABLE IF NOT EXISTS users (
      id SERIAL PRIMARY KEY,
      email VARCHAR(255) UNIQUE NOT NULL,
      password_hash VARCHAR(255) NOT NULL,
      hwid VARCHAR(255) UNIQUE,
      is_pro BOOLEAN DEFAULT FALSE,
      subscription_expires_at TIMESTAMP,
      created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    );

    CREATE TABLE IF NOT EXISTS checkpoints (
      id UUID PRIMARY KEY DEFAULT gen_random_ What_uuid(),
      title VARCHAR(255) NOT NULL,
      reporter_hwid VARCHAR(255),
      location GEOMETRY(Point, 4362),
      upvotes INT DEFAULT 1,
      downvotes INT DEFAULT 0,
      is_active BOOLEAN DEFAULT TRUE,
      created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    );
  `);
}

// HWID Lock Middleware (Strict 1 device per account constraint)
function verifyHwidToken(req, res, next) {
  const authHeader = req.headers['authorization'];
  if (!authHeader) return res.status(401).json({ error: 'Missing authorization header' });

  const token = authHeader.split(' ')[1];
  jwt.verify(token, JWT_SECRET, (err, decoded) => {
    if (err) return res.status(403).json({ error: 'Invalid or expired token' });
    
    const requestHwid = req.headers['x-hwid'];
    if (decoded.hwid && decoded.hwid !== requestHwid) {
      return res.status(403).json({ error: 'HWID mismatch. Device not authorized for this account.' });
    }
    req.user = decoded;
    next();
  });
}

// AUTH: Login with HWID binding
app.post('/api/v1/auth/login', async (req, res) => {
  const { email, password, hwid } = req.body;
  try {
    const result = await db.query('SELECT * FROM users WHERE email = $1', [email]);
    if (result.rows.length === 0) return res.status(401).json({ error: 'User not found' });

    val user = result.rows[0];
    const validPass = await bcrypt.compare(password, user.password_hash);
    if (!validPass) return res.status(401).json({ error: 'Invalid credentials' });

    // HWID Binding
    if (!user.hwid) {
      await db.query('UPDATE users SET hwid = $1 WHERE id = $2', [hwid, user.id]);
    } else if (user.hwid !== hwid) {
      return res.status(403).json({ error: 'Account registered to a different device (HWID locked).' });
    }

    const token = jwt.sign(
      { userId: user.id, email: user.email, hwid: hwid, isPro: user.is_pro },
      JWT_SECRET,
      { expiresIn: '30d' }
    );

    res.json({ token, isPro: user.is_pro, hwid: hwid });
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

// GEOSPATIAL: Get Nearby Checkpoints with PostGIS ST_DWithin query
app.get('/api/v1/checkpoints/nearby', verifyHwidToken, async (req, res) => {
  const { lat, lng, radiusKm = 10 } = req.query;
  try {
    const query = `
      SELECT id, title, reporter_hwid, upvotes, downvotes,
             ST_Y(location::geometry) as latitude,
             ST_X(location::geometry) as longitude,
             created_at
      FROM checkpoints
      WHERE is_active = TRUE
        AND ST_DWithin(location, ST_MakePoint($1, $2)::geography, $3 * 1000)
      ORDER BY created_at DESC;
    `;
    const result = await db.query(query, [lng, lat, radiusKm]);
    res.json(result.rows);
  } catch (err) {
    res.status(500).json({ error: err.message });
  }
});

app.listen(3000, () => console.log('GPS Setter Pro Backend API running on port 3000'));
"""

    // Android Local Mock/Remote API Response Models
    data class AuthResponse(
        val token: String,
        val isPro: Boolean,
        val hwid: String,
        val userEmail: String
    )

    data class UserProfile(
        val email: String,
        val hwid: String,
        val isPro: Boolean,
        val planName: String = "Pro Professional License",
        val expiresAt: String = "2027-12-31"
    )
}
