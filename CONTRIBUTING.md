# Contributing to SaneLink 🛡️

Thank you for helping keep links clean and private for everyone!

SaneLink is designed so that **anyone can contribute new websites and parameter filters** by simply editing a single file: [`rules/rules.json`](rules/rules.json).

You **do not need Android Studio, Gradle, or an Android device** installed on your machine to contribute rules.

---

## How to Add or Update Rules

### 1. Locate the Rules File
All rules live in:
```
rules/rules.json
```

### 2. Rule Structure

#### A. Universal Tracking Parameters
If a marketing or analytics tag is used universally across the internet (e.g., `utm_source`, `fbclid`), add it to `"universalParameters"`:
```json
"universalParameters": [
  "utm_source",
  "fbclid",
  "your_new_tracker_param"
]
```

#### B. Adding a New Website
Add a new object inside the `"sites"` array:
```json
{
  "id": "my_service",
  "name": "My Service",
  "description": "Removes tracking and sharing query parameters",
  "domains": [
    "example.com",
    "m.example.com"
  ],
  "stripParameters": [
    "ref",
    "tracking_token",
    "share_id"
  ]
}
```

#### C. Preserving Specific Parameters (Whitelist)
If a site uses many parameters but you only want to keep a few essential ones (like YouTube timestamps):
```json
{
  "id": "video_site",
  "name": "Video Site",
  "domains": ["videos.com"],
  "stripParameters": ["si", "tracking"],
  "keepParameters": ["v", "t"]
}
```

#### D. Unwrapping Redirect Links
If a service wraps destination URLs inside a redirect link (like `https://example.com/redirect?target=https://destination.com`):
```json
{
  "id": "example_redirect",
  "name": "Example Redirect Unwrap",
  "domains": ["example.com"],
  "paths": ["/redirect"],
  "unwrapParameter": "target"
}
```

#### E. Cleaning URL Paths (e.g. Amazon ASIN)
To normalize URL paths and remove bloat:
```json
{
  "id": "shop",
  "name": "Shop",
  "domains": ["shop.com"],
  "stripParameters": ["ref"],
  "canonicalPathRegex": ".*/product/([A-Z0-9]+).*",
  "canonicalPathReplacement": "/product/$1"
}
```

---

## Opening a Pull Request

1. Fork this repository on GitHub.
2. Edit `rules/rules.json` (you can do this directly in your web browser on GitHub!).
3. Submit a Pull Request with a clear title (e.g., `Add rules for Threads` or `Remove eBay affiliate tags`).
4. That's it! Our automated tests will validate the JSON schema and rules.
