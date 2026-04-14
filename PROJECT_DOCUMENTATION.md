# Our Story Site Documentation

## Overview

This project is a small private website made in **Java + HTML + CSS + JavaScript** for a couple.

It includes:

- A private login page
- Access limited to **two users**
- A dashboard
- A **Birthdays** page
- A **Trips** page
- A **Future Plans** page
- A romantic modern UI with responsive design

The app is built from scratch using Java's built-in `HttpServer`, so it does **not require Spring Boot or external libraries**.

This version has been adjusted to be **Java 7 compatible**.

## Project Structure

```text
h:\website
|-- pom.xml
|-- PROJECT_DOCUMENTATION.md
`-- src
    `-- main
        |-- java
        |   `-- com
        |       `-- couplememory
        |           `-- OurStorySiteApplication.java
        `-- resources
            |-- static
            |   `-- assets
            |       |-- app.js
            |       `-- styles.css
            `-- templates
                |-- birthdays.html
                |-- dashboard.html
                |-- fallback.html
                |-- login.html
                |-- plans.html
                `-- trips.html
```

## Private Login

Only two users are allowed.

Current default usernames and passwords:

- User 1: `partner1`
- Password 1: `forever123`
- User 2: `partner2`
- Password 2: `always456`

You should change these before using the website privately.

## How to change the login

Open:

- `src/main/java/com/couplememory/OurStorySiteApplication.java`

Look for:

```java
USERS.put(readEnv("OUR_STORY_USER_ONE", "partner1"), readEnv("OUR_STORY_PASS_ONE", "forever123"));
USERS.put(readEnv("OUR_STORY_USER_TWO", "partner2"), readEnv("OUR_STORY_PASS_TWO", "always456"));
```

You can either:

1. Change the hardcoded fallback values directly in the Java file.
2. Set environment variables:

```powershell
$env:OUR_STORY_USER_ONE="your_name"
$env:OUR_STORY_PASS_ONE="your_password"
$env:OUR_STORY_USER_TWO="girlfriend_name"
$env:OUR_STORY_PASS_TWO="her_password"
```

## Content Sections

### Dashboard

Contains:

- Welcome section
- Relationship stats
- Quick navigation cards
- A hidden decoded note

### Birthdays

Edit the `BIRTHDAY_MOMENTS` list in `OurStorySiteApplication.java`.

### Trips

Edit the `TRIPS` list in `OurStorySiteApplication.java`.

### Future Plans

Edit the `FUTURE_PLANS` list in `OurStorySiteApplication.java`.

## Design

The website uses:

- Warm romantic colors
- Glass-style cards
- Large editorial fonts
- Soft reveal animations
- Responsive layouts for mobile and desktop

Main design files:

- `src/main/resources/static/assets/styles.css`
- `src/main/resources/static/assets/app.js`

## How to Run

If Java 7 and Maven are installed:

```powershell
mvn compile
```

Then run the main class from your IDE, or compile and run manually:

```powershell
javac -d out src/main/java/com/couplememory/OurStorySiteApplication.java
java -cp out com.couplememory.OurStorySiteApplication
```

Open this in your browser:

```text
http://localhost:8080
```

## Important Note

This workspace did not have Java, Maven, or Gradle installed while the project was created, so the project could not be executed here.

Install Java 7 before running it locally.
