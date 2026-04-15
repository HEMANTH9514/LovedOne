package com.couplememory;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Properties;
import java.text.SimpleDateFormat;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

public class OurStorySiteApplication {
    private static final int PORT = readEnvInt("PORT", 8080);
    private static final String SESSION_COOKIE = "our-story-session";
    private static final String LOGIN_TITLE = "Our Story Login";
    private static final Charset UTF_8 = Charset.forName("UTF-8");
    private static final Logger LOG = Logger.getLogger(OurStorySiteApplication.class.getName());

    private static final Map<String, String> USERS = new LinkedHashMap<String, String>();
    private static final Map<String, String> SESSIONS = new ConcurrentHashMap<String, String>();
    private static final List<MemoryCard> FUTURE_PLANS = Arrays.asList(
            new MemoryCard("Anniversary Dinner Plan", "Dress up, revisit our favorite songs, and end the night with a handwritten letter exchange.", "Next milestone", "Elegant and Intimate"),
            new MemoryCard("Passport Dream Trip", "A longer trip where we unplug, take film photos, and collect tiny keepsakes from each place.", "This year", "Adventure Chapter"),
            new MemoryCard("Memory Wall Project", "Print our favorite pictures and build a physical timeline of us at home.", "Next month", "Creative Weekend")
    );

    public static void main(String[] args) throws IOException {
        LOG.info("Application startup initiated.");
        configureUsers();

        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.createContext("/", new RouteHandler());
        server.setExecutor(Executors.newFixedThreadPool(10));
        server.start();

        LOG.info("HTTP server started on port " + PORT);
        System.out.println("Our Story Site is running at http://localhost:" + PORT);
        System.out.println("Configured private users: " + joinKeys(USERS));
        LOG.info("Configured private users: " + joinKeys(USERS));
    }

    private static void configureUsers() {
        USERS.put(readEnv("OUR_STORY_USER_ONE", "partner1"), readEnv("OUR_STORY_PASS_ONE", "change-me-1"));
        USERS.put(readEnv("OUR_STORY_USER_TWO", "partner2"), readEnv("OUR_STORY_PASS_TWO", "change-me-2"));
        LOG.info("User configuration loaded.");
    }

    private static String readEnv(String key, String fallback) {
        String value = System.getenv(key);
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }
        return value;
    }

    private static int readEnvInt(String key, int fallback) {
        String value = System.getenv(key);
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static String joinKeys(Map<String, String> map) {
        StringBuilder builder = new StringBuilder();
        for (String key : map.keySet()) {
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(key);
        }
        return builder.toString();
    }

    private static class RouteHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            try {
                String path = exchange.getRequestURI().getPath();
                String method = exchange.getRequestMethod();
                LOG.info("Incoming request: " + method + " " + path);

                if (path.startsWith("/assets/")) {
                    serveAsset(exchange, path);
                    return;
                }

                if ("/login".equals(path) && "POST".equalsIgnoreCase(method)) {
                    handleLogin(exchange);
                    return;
                }

                if (path.startsWith("/upload/") && "POST".equalsIgnoreCase(method)) {
                    handleUpload(exchange, path);
                    return;
                }

                if ("/manage/delete".equals(path) && "POST".equalsIgnoreCase(method)) {
                    handleDeleteImage(exchange);
                    return;
                }

                if ("/logout".equals(path)) {
                    handleLogout(exchange);
                    return;
                }

                String username = currentUser(exchange);
                LOG.info("Session user resolved: " + (username == null ? "anonymous" : username));
                if (username == null && !"/".equals(path)) {
                    LOG.info("Unauthenticated access to protected path. Redirecting to login.");
                    redirect(exchange, "/");
                    return;
                }

                if ("/".equals(path)) {
                    LOG.info("Serving root path.");
                    showLoginOrDashboard(exchange, username);
                } else if ("/dashboard".equals(path)) {
                    LOG.info("Serving dashboard for user: " + username);
                    renderPage(exchange, "dashboard.html", pageModel("dashboard", username, null));
                } else if ("/birthdays".equals(path)) {
                    LOG.info("Serving birthdays year list.");
                    renderPage(exchange, "birthdays.html", sectionModel("birthdays", username, "Birthdays by Year",
                            "Open a year and see every birthday photo saved in that folder.", renderYearCards("birthdays")));
                } else if (path.startsWith("/birthdays/")) {
                    LOG.info("Serving birthdays gallery for year path: " + path);
                    renderPage(exchange, "gallery.html", galleryModel(exchange, "birthdays", username, "Birthday Gallery",
                            "Photos saved inside your selected birthday year folder.", "birthdays", path.substring("/birthdays/".length())));
                } else if ("/trips".equals(path)) {
                    LOG.info("Serving trips year list.");
                    renderPage(exchange, "trips.html", sectionModel("trips", username, "Trips by Year",
                            "Open a year and see every trip photo saved in that folder.", renderYearCards("trips")));
                } else if (path.startsWith("/trips/")) {
                    LOG.info("Serving trips gallery for year path: " + path);
                    renderPage(exchange, "gallery.html", galleryModel(exchange, "trips", username, "Trip Gallery",
                            "Photos saved inside your selected trip year folder.", "trips", path.substring("/trips/".length())));
                } else if ("/plans".equals(path)) {
                    LOG.info("Serving plans page.");
                    renderPage(exchange, "plans.html", plansModel(exchange, username));
                } else if ("/manage/delete".equals(path)) {
                    if (!isHemanth(username)) {
                        LOG.info("Non-Hemanth user attempted delete manager access. Redirecting.");
                        redirect(exchange, "/dashboard");
                        return;
                    }
                    LOG.info("Serving delete manager page for Hemanth.");
                    renderPage(exchange, "manage-delete.html", deleteManagerModel(exchange, username));
                } else {
                    LOG.info("Route not found: " + path);
                    sendHtml(exchange, 404, TemplateEngine.wrap("Page Not Found", "<section class=\"message-card\"><h1>Page not found</h1><p>The page you requested does not exist.</p><a class=\"ghost-link\" href=\"/dashboard\">Back to dashboard</a></section>"));
                }
            } catch (Exception exception) {
                LOG.severe("Unhandled server error: " + exception.getMessage());
                sendHtml(exchange, 500, TemplateEngine.wrap("Server Error", "<section class=\"message-card\"><h1>Something went wrong</h1><p>" + escapeHtml(exception.getMessage()) + "</p></section>"));
            } finally {
                exchange.close();
            }
        }
    }

    private static void showLoginOrDashboard(HttpExchange exchange, String username) throws IOException {
        if (username != null) {
            LOG.info("User already authenticated at root. Redirecting to dashboard: " + username);
            redirect(exchange, "/dashboard");
            return;
        }

        LOG.info("Rendering login page for anonymous user.");
        Map<String, String> model = new HashMap<String, String>();
        model.put("title", LOGIN_TITLE);
        model.put("error", readQueryParam(exchange.getRequestURI().getQuery(), "error"));
        renderPage(exchange, "login.html", model);
    }

    private static void handleLogin(HttpExchange exchange) throws IOException {
        String form = new String(readAll(exchange.getRequestBody()), UTF_8);
        Map<String, String> params = parseForm(form);
        String username = valueOrEmpty(params.get("username")).trim();
        String password = valueOrEmpty(params.get("password")).trim();
        LOG.info("Login attempt for username: " + username);

        if (USERS.containsKey(username) && USERS.get(username).equals(password)) {
            String sessionId = UUID.randomUUID().toString();
            SESSIONS.put(sessionId, username);
            exchange.getResponseHeaders().add("Set-Cookie", SESSION_COOKIE + "=" + sessionId + "; Path=/; HttpOnly");
            LOG.info("Login success for username: " + username);
            redirect(exchange, "/dashboard");
            return;
        }

        LOG.info("Login failed for username: " + username);
        redirect(exchange, "/?error=Private+login+failed.+Please+check+your+username+and+password.");
    }

    private static void handleLogout(HttpExchange exchange) throws IOException {
        String sessionId = readCookie(exchange, SESSION_COOKIE);
        if (sessionId != null) {
            LOG.info("Logout requested for session: " + sessionId);
            SESSIONS.remove(sessionId);
        }
        exchange.getResponseHeaders().add("Set-Cookie", SESSION_COOKIE + "=deleted; Path=/; Max-Age=0; HttpOnly");
        LOG.info("Logout completed.");
        redirect(exchange, "/");
    }

    private static void handleUpload(HttpExchange exchange, String path) throws IOException {
        LOG.info("Upload request received for path: " + path);
        String[] segments = path.split("/");
        if (segments.length != 4) {
            LOG.info("Upload request rejected: invalid path format.");
            redirect(exchange, "/dashboard");
            return;
        }

        String section = sanitizeSection(segments[2]);
        String year = sanitizeYear(segments[3]);
        if (section.isEmpty() || year.isEmpty()) {
            LOG.info("Upload request rejected: invalid section/year.");
            redirect(exchange, "/dashboard");
            return;
        }

        Headers headers = exchange.getRequestHeaders();
        String contentType = headers.getFirst("Content-Type");
        if (contentType == null || contentType.indexOf("multipart/form-data") == -1) {
            LOG.info("Upload request rejected: invalid content type.");
            redirect(exchange, "/" + section + "/" + year + "?message=Upload+failed.+Please+use+the+upload+form.");
            return;
        }

        String boundary = extractBoundary(contentType);
        if (boundary.isEmpty()) {
            LOG.info("Upload request rejected: missing multipart boundary.");
            redirect(exchange, "/" + section + "/" + year + "?message=Upload+failed.+Missing+upload+boundary.");
            return;
        }

        byte[] body = readAll(exchange.getRequestBody());
        Map<String, MultipartItem> parts = parseMultipart(body, boundary);
        MultipartItem imagePart = parts.get("image");
        MultipartItem captionPart = parts.get("caption");

        if (imagePart == null || imagePart.getFileName().isEmpty() || imagePart.getData().length == 0) {
            LOG.info("Upload request rejected: missing image payload.");
            redirect(exchange, "/" + section + "/" + year + "?message=Please+choose+an+image+before+uploading.");
            return;
        }

        String safeFileName = sanitizeFileName(imagePart.getFileName());
        if (safeFileName.isEmpty() || !isImageFile(safeFileName)) {
            LOG.info("Upload request rejected: unsupported file type for " + safeFileName);
            redirect(exchange, "/" + section + "/" + year + "?message=Only+image+files+are+allowed.");
            return;
        }

        java.nio.file.Path yearFolder = photosYearPath(section, year);
        Files.createDirectories(yearFolder);
        Files.write(yearFolder.resolve(safeFileName), imagePart.getData());

        String caption = captionPart == null ? "" : captionPart.asText();
        String uploader = valueOrEmpty(currentUser(exchange)).trim();
        if (uploader.isEmpty()) {
            uploader = "Private user";
        }
        updateCaptionFile(section, year, safeFileName, caption, uploader);
        LOG.info("Upload successful: section=" + section + ", year=" + year + ", file=" + safeFileName);
        redirect(exchange, "/" + section + "/" + year + "?message=Upload+saved+successfully.");
    }

    private static void handleDeleteImage(HttpExchange exchange) throws IOException {
        String username = currentUser(exchange);
        if (!isHemanth(username)) {
            LOG.info("Delete request blocked for non-Hemanth user.");
            redirect(exchange, "/dashboard");
            return;
        }
        LOG.info("Delete request received from Hemanth.");

        String form = new String(readAll(exchange.getRequestBody()), UTF_8);
        Map<String, String> params = parseForm(form);
        String section = sanitizeSection(valueOrEmpty(params.get("section")));
        String year = sanitizeYear(valueOrEmpty(params.get("year")));
        String file = sanitizeFileName(valueOrEmpty(params.get("file")));

        if (section.isEmpty() || year.isEmpty() || file.isEmpty()) {
            LOG.info("Delete request rejected: invalid section/year/file payload.");
            redirect(exchange, "/manage/delete?message=Delete+failed.+Invalid+request.");
            return;
        }

        java.nio.file.Path filePath = photosYearPath(section, year).resolve(file);
        if (!Files.exists(filePath)) {
            LOG.info("Delete request failed: file not found " + filePath);
            redirect(exchange, "/manage/delete?message=Delete+failed.+File+not+found.");
            return;
        }

        Files.delete(filePath);
        removeCaptionEntry(section, year, file);
        LOG.info("Delete successful: section=" + section + ", year=" + year + ", file=" + file);
        redirect(exchange, "/manage/delete?message=Image+deleted+successfully.");
    }

    private static Map<String, String> pageModel(String activePage, String username, String cardsMarkup) {
        Map<String, String> model = new HashMap<String, String>();
        model.put("title", "Our Story");
        model.put("username", username == null ? "Private Guest" : username);
        
        String greetingHtml = "";
        if ("dashboard".equals(activePage) && username != null) {
            if (isHemanth(username)) {
                greetingHtml = "<section class=\"welcome-greeting reveal\"><h1>Hi Hemanth</h1></section>";
            } else if (username.equalsIgnoreCase("Prashanti")) {
                greetingHtml = "<section class=\"welcome-greeting reveal\"><h1>Welcome Prashanti</h1><img class=\"welcome-gif\" src=\"/assets/prashanti-welcome.gif?v=20260415\" alt=\"Warm welcome\" /></section>";
            }
        }
        model.put("greetingHtml", greetingHtml);
        model.put("latestUpdatesHtml", "dashboard".equals(activePage) ? renderLatestUpdates() : "");
        model.put("manageDeleteHtml", ("dashboard".equals(activePage) && isHemanth(username))
                ? "<a class=\"ghost-link\" href=\"/manage/delete\">Delete Images (Hemanth)</a>"
                : "");

        model.put("activeDashboard", "dashboard".equals(activePage) ? "nav-link active" : "nav-link");
        model.put("activeBirthdays", "birthdays".equals(activePage) ? "nav-link active" : "nav-link");
        model.put("activeTrips", "trips".equals(activePage) ? "nav-link active" : "nav-link");
        model.put("activePlans", "plans".equals(activePage) ? "nav-link active" : "nav-link");
        model.put("cards", cardsMarkup == null ? "" : cardsMarkup);
        model.put("daysTogether", String.valueOf(calculateDaysTogether()));
        model.put("coverMessage", "You two built something worth keeping beautifully documented.");
        return model;
    }

    private static Map<String, String> sectionModel(String activePage, String username, String pageTitle, String lead, String cardsMarkup) {
        Map<String, String> model = pageModel(activePage, username, cardsMarkup);
        model.put("pageTitle", pageTitle);
        model.put("pageLead", lead);
        return model;
    }

    private static Map<String, String> galleryModel(HttpExchange exchange, String activePage, String username, String pageTitle, String lead, String section, String year) throws IOException {
        Map<String, String> model = pageModel(activePage, username, null);
        String safeYear = sanitizeYear(year);
        model.put("pageTitle", pageTitle);
        model.put("pageLead", lead);
        model.put("currentYear", safeYear);
        model.put("backLink", "/" + section);
        model.put("galleryItems", renderGallery(section, safeYear));
        model.put("folderPath", "/assets/photos/" + section + "/" + safeYear);
        model.put("captionPath", "/assets/photos/" + section + "/" + safeYear + "/captions.properties");
        model.put("uploadAction", "/upload/" + section + "/" + safeYear);
        model.put("uploadMessage", readQueryParam(exchange.getRequestURI().getQuery(), "message"));
        return model;
    }

    private static Map<String, String> deleteManagerModel(HttpExchange exchange, String username) throws IOException {
        Map<String, String> model = pageModel("dashboard", username, null);
        model.put("title", "Manage Images");
        model.put("pageTitle", "Delete Images");
        model.put("pageLead", "Choose any birthday, trip, or plan image and remove it.");
        model.put("deleteItems", renderDeleteManager());
        model.put("manageMessage", readQueryParam(exchange.getRequestURI().getQuery(), "message"));
        return model;
    }

    private static Map<String, String> plansModel(HttpExchange exchange, String username) throws IOException {
        Map<String, String> model = pageModel("plans", username, null);
        model.put("title", "Our Story Plans");
        model.put("uploadAction", "/upload/plans/board");
        model.put("uploadMessage", readQueryParam(exchange.getRequestURI().getQuery(), "message"));
        model.put("galleryItems", renderGallery("plans", "board"));
        return model;
    }

    private static long calculateDaysTogether() {
        Calendar start = Calendar.getInstance();
        start.set(Calendar.YEAR, 2016);
        start.set(Calendar.MONTH, Calendar.JUNE);
        start.set(Calendar.DAY_OF_MONTH, 1);
        start.set(Calendar.HOUR_OF_DAY, 0);
        start.set(Calendar.MINUTE, 0);
        start.set(Calendar.SECOND, 0);
        start.set(Calendar.MILLISECOND, 0);

        long difference = System.currentTimeMillis() - start.getTimeInMillis();
        return difference / (1000L * 60L * 60L * 24L);
    }

    private static String currentUser(HttpExchange exchange) {
        String sessionId = readCookie(exchange, SESSION_COOKIE);
        return sessionId == null ? null : SESSIONS.get(sessionId);
    }

    private static String readCookie(HttpExchange exchange, String name) {
        List<String> cookies = exchange.getRequestHeaders().get("Cookie");
        if (cookies == null) {
            return null;
        }

        for (String cookieHeader : cookies) {
            String[] entries = cookieHeader.split(";");
            for (String entry : entries) {
                String[] pair = entry.trim().split("=", 2);
                if (pair.length == 2 && pair[0].trim().equals(name)) {
                    return pair[1].trim();
                }
            }
        }
        return null;
    }

    private static Map<String, String> parseForm(String body) throws IOException {
        Map<String, String> result = new HashMap<String, String>();
        if (body == null || body.trim().isEmpty()) {
            return result;
        }

        for (String pair : body.split("&")) {
            String[] parts = pair.split("=", 2);
            String key = urlDecode(parts[0]);
            String value = parts.length > 1 ? urlDecode(parts[1]) : "";
            result.put(key, value);
        }
        return result;
    }

    private static Map<String, MultipartItem> parseMultipart(byte[] body, String boundary) throws IOException {
        Map<String, MultipartItem> parts = new LinkedHashMap<String, MultipartItem>();
        byte[] boundaryBytes = ("--" + boundary).getBytes(StandardCharsets.ISO_8859_1);
        byte[] headerSeparator = "\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1);
        int cursor = indexOf(body, boundaryBytes, 0);

        while (cursor >= 0) {
            int partStart = cursor + boundaryBytes.length;
            if (partStart + 1 < body.length && body[partStart] == '-' && body[partStart + 1] == '-') {
                break;
            }
            if (partStart + 1 < body.length && body[partStart] == '\r' && body[partStart + 1] == '\n') {
                partStart += 2;
            }

            int nextBoundary = indexOf(body, boundaryBytes, partStart);
            if (nextBoundary < 0) {
                break;
            }

            int headerEnd = indexOf(body, headerSeparator, partStart);
            if (headerEnd < 0 || headerEnd > nextBoundary) {
                break;
            }

            String headerText = new String(body, partStart, headerEnd - partStart, StandardCharsets.ISO_8859_1);
            Map<String, String> metadata = parsePartHeaders(headerText);
            int dataStart = headerEnd + headerSeparator.length;
            int dataEnd = nextBoundary;
            if (dataEnd - 2 >= dataStart && body[dataEnd - 2] == '\r' && body[dataEnd - 1] == '\n') {
                dataEnd -= 2;
            }

            byte[] data = Arrays.copyOfRange(body, dataStart, dataEnd);
            String name = valueOrEmpty(metadata.get("name"));
            if (!name.isEmpty()) {
                parts.put(name, new MultipartItem(name, valueOrEmpty(metadata.get("filename")), data));
            }

            cursor = nextBoundary;
        }

        return parts;
    }

    private static Map<String, String> parsePartHeaders(String headerText) {
        Map<String, String> metadata = new HashMap<String, String>();
        String[] lines = headerText.split("\r\n");
        for (String line : lines) {
            if (line.toLowerCase().startsWith("content-disposition:")) {
                String[] segments = line.split(";");
                for (String segment : segments) {
                    String trimmed = segment.trim();
                    if (trimmed.startsWith("name=")) {
                        metadata.put("name", stripQuotes(trimmed.substring(5)));
                    } else if (trimmed.startsWith("filename=")) {
                        metadata.put("filename", stripQuotes(trimmed.substring(9)));
                    }
                }
            }
        }
        return metadata;
    }

    private static String stripQuotes(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.startsWith("\"") && trimmed.endsWith("\"") && trimmed.length() >= 2) {
            return trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed;
    }

    private static int indexOf(byte[] source, byte[] target, int fromIndex) {
        outer:
        for (int i = fromIndex; i <= source.length - target.length; i++) {
            for (int j = 0; j < target.length; j++) {
                if (source[i + j] != target[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    private static String readQueryParam(String query, String key) throws IOException {
        if (query == null || query.trim().isEmpty()) {
            return "";
        }

        for (String pair : query.split("&")) {
            String[] parts = pair.split("=", 2);
            if (parts.length > 0 && key.equals(urlDecode(parts[0]))) {
                return parts.length > 1 ? escapeHtml(urlDecode(parts[1])) : "";
            }
        }
        return "";
    }

    private static String urlDecode(String value) throws IOException {
        return URLDecoder.decode(value, "UTF-8");
    }

    private static void renderPage(HttpExchange exchange, String templateName, Map<String, String> model) throws IOException {
        String template = readResource("/templates/" + templateName);
        String html = TemplateEngine.render(template, model);
        sendHtml(exchange, 200, html);
    }

    private static void serveAsset(HttpExchange exchange, String path) throws IOException {
        String resourcePath = "/static" + path;
        String contentType = contentType(path);
        byte[] content = readResourceBytes(resourcePath);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(200, content.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(content);
        }
    }

    private static void sendHtml(HttpExchange exchange, int status, String html) throws IOException {
        byte[] payload = html.getBytes(UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
        exchange.sendResponseHeaders(status, payload.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(payload);
        }
    }

    private static void redirect(HttpExchange exchange, String location) throws IOException {
        Headers headers = exchange.getResponseHeaders();
        headers.set("Location", location);
        exchange.sendResponseHeaders(302, -1);
    }

    private static String renderCards(List<MemoryCard> cards) {
        List<String> markup = new ArrayList<String>();
        for (MemoryCard card : cards) {
            StringBuilder builder = new StringBuilder();
            builder.append("<article class=\"story-card reveal\">");
            builder.append("<span class=\"story-tag\">").append(escapeHtml(card.getTag())).append("</span>");
            builder.append("<h3>").append(escapeHtml(card.getTitle())).append("</h3>");
            builder.append("<p>").append(escapeHtml(card.getDescription())).append("</p>");
            builder.append("<footer><span>").append(escapeHtml(card.getMeta())).append("</span></footer>");
            builder.append("</article>");
            markup.add(builder.toString());
        }
        return joinStrings(markup, "\n");
    }

    private static String renderYearCards(String section) {
        List<String> years = listYearFolders(section);
        List<String> markup = new ArrayList<String>();

        for (String year : years) {
            int imageCount = countImages(section, year);
            StringBuilder builder = new StringBuilder();
            builder.append("<a class=\"year-card reveal\" href=\"/").append(section).append("/").append(escapeHtml(year)).append("\">");
            builder.append("<span class=\"mini-label\">").append(section.equals("birthdays") ? "Birthday Year" : "Trip Year").append("</span>");
            builder.append("<h2>").append(escapeHtml(year)).append("</h2>");
            builder.append("<p>");
            builder.append(imageCount).append(imageCount == 1 ? " image" : " images");
            builder.append(" inside this folder.</p>");
            builder.append("<strong>Open ").append(escapeHtml(year)).append("</strong>");
            builder.append("</a>");
            markup.add(builder.toString());
        }

        if (markup.isEmpty()) {
            markup.add("<section class=\"message-card\"><h2>No year folders yet</h2><p>Create folders inside <code>/assets/photos/" + escapeHtml(section) + "</code> and name them like <code>2024</code>, <code>2025</code>.</p></section>");
        }

        return joinStrings(markup, "\n");
    }

    private static String renderLatestUpdates() {
        List<FeedItem> items = collectLatestFeedItems();
        List<String> markup = new ArrayList<String>();
        markup.add("<section class=\"latest-feed reveal\">");
        markup.add("<h2>Latest Updates</h2>");
        markup.add("<p class=\"lead\">Recent birthday, trip, and plan memories appear here automatically.</p>");

        if (items.isEmpty()) {
            markup.add("<p class=\"file-text\">No updates yet. Upload a memory to get started.</p>");
        } else {
            markup.add("<div class=\"latest-feed-list\">");
            int count = Math.min(6, items.size());
            for (int i = 0; i < count; i++) {
                FeedItem item = items.get(i);
                markup.add("<a class=\"latest-feed-item\" href=\"" + escapeHtml(item.getYearUrl()) + "\">");
                markup.add("<img src=\"" + escapeHtml(item.getImageUrl()) + "\" alt=\"" + escapeHtml(item.getFileName()) + "\" />");
                markup.add("<div>");
                markup.add("<strong>" + escapeHtml(item.getSectionLabel()) + " " + escapeHtml(item.getYear()) + "</strong>");
                markup.add("<p>" + escapeHtml(item.getCaption().isEmpty() ? "New memory added." : item.getCaption()) + "</p>");
                markup.add("<span>" + escapeHtml(item.getDateLabel()) + " • by " + escapeHtml(item.getUploader()) + "</span>");
                markup.add("</div>");
                markup.add("</a>");
            }
            markup.add("</div>");
        }

        markup.add("</section>");
        return joinStrings(markup, "\n");
    }

    private static String renderGallery(String section, String year) {
        List<File> images = listImages(section, year);
        Map<String, String> captions = loadCaptions(section, year);
        List<String> markup = new ArrayList<String>();

        for (File image : images) {
            String fileName = image.getName();
            String imageUrl = "/assets/photos/" + section + "/" + year + "/" + fileName;
            String caption = resolveCaption(captions, fileName);
            String uploader = resolveUploader(captions, fileName);
            StringBuilder builder = new StringBuilder();
            builder.append("<article class=\"gallery-card insta-post reveal\">");
            builder.append("<div class=\"post-head\">");
            builder.append("<img class=\"post-avatar-logo\" src=\"/assets/logo.svg?v=20260415\" alt=\"HP logo\" />");
            builder.append("<div class=\"post-meta\">");
            builder.append("<strong>").append(escapeHtml("plans".equals(section) ? "Plans" : year)).append("</strong>");
            builder.append("<span>").append(section.equals("birthdays") ? "Birthday memory" : section.equals("trips") ? "Trip memory" : "Plan memory").append("</span>");
            builder.append("</div>");
            builder.append("</div>");
            builder.append("<a class=\"gallery-image\" href=\"").append(escapeHtml(imageUrl)).append("\" target=\"_blank\">");
            builder.append("<img src=\"").append(escapeHtml(imageUrl)).append("\" alt=\"").append(escapeHtml(fileName)).append("\" />");
            builder.append("</a>");
            builder.append("<div class=\"gallery-copy\">");
            builder.append("<h3>Uploaded by ").append(escapeHtml(uploader)).append("</h3>");
            builder.append("<p class=\"caption-text\">").append(caption.isEmpty() ? "Add a caption for this image in captions.properties." : escapeHtml(caption)).append("</p>");
            builder.append("</div>");
            builder.append("</article>");
            markup.add(builder.toString());
        }

        if (markup.isEmpty()) {
            markup.add("<section class=\"message-card\"><h2>No memories yet</h2><p>Add image files to <code>/assets/photos/" + escapeHtml(section) + "/" + escapeHtml(year) + "</code> and refresh this page.</p></section>");
        }

        return joinStrings(markup, "\n");
    }

    private static String renderDeleteManager() {
        List<FeedItem> items = collectLatestFeedItems();
        List<String> markup = new ArrayList<String>();
        if (items.isEmpty()) {
            return "<section class=\"message-card\"><h2>No images to delete</h2><p>Upload images first, then they will appear here.</p></section>";
        }

        markup.add("<section class=\"delete-grid\">");
        for (FeedItem item : items) {
            markup.add("<article class=\"delete-card reveal\">");
            markup.add("<img src=\"" + escapeHtml(item.getImageUrl()) + "\" alt=\"" + escapeHtml(item.getFileName()) + "\" />");
            markup.add("<div class=\"delete-copy\">");
            markup.add("<h3>" + escapeHtml(item.getSectionLabel()) + " " + escapeHtml(item.getYear()) + "</h3>");
            markup.add("<p>" + escapeHtml(item.getFileName()) + "</p>");
            markup.add("<form method=\"post\" action=\"/manage/delete\">");
            markup.add("<input type=\"hidden\" name=\"section\" value=\"" + escapeHtml(item.getSection()) + "\" />");
            markup.add("<input type=\"hidden\" name=\"year\" value=\"" + escapeHtml(item.getYear()) + "\" />");
            markup.add("<input type=\"hidden\" name=\"file\" value=\"" + escapeHtml(item.getFileName()) + "\" />");
            markup.add("<button class=\"delete-btn\" type=\"submit\">Delete Image</button>");
            markup.add("</form>");
            markup.add("</div>");
            markup.add("</article>");
        }
        markup.add("</section>");
        return joinStrings(markup, "\n");
    }

    private static List<FeedItem> collectLatestFeedItems() {
        List<FeedItem> items = new ArrayList<FeedItem>();
        collectSectionFeed("birthdays", "Birthday", items);
        collectSectionFeed("trips", "Trip", items);
        collectSectionFeed("plans", "Plan", items);

        Collections.sort(items, new Comparator<FeedItem>() {
            public int compare(FeedItem left, FeedItem right) {
                if (left.getLastModified() == right.getLastModified()) {
                    return right.getFileName().compareToIgnoreCase(left.getFileName());
                }
                return left.getLastModified() > right.getLastModified() ? -1 : 1;
            }
        });

        return items;
    }

    private static void collectSectionFeed(String section, String label, List<FeedItem> items) {
        List<String> years = listYearFolders(section);
        for (String year : years) {
            Map<String, String> captions = loadCaptions(section, year);
            List<File> images = listImages(section, year);
            for (File image : images) {
                String fileName = image.getName();
                String caption = resolveCaption(captions, fileName);
                String uploader = resolveUploader(captions, fileName);
                items.add(new FeedItem(
                        section,
                        label,
                        year,
                        fileName,
                        "/assets/photos/" + section + "/" + year + "/" + fileName,
                        "/" + section + "/" + year,
                        caption,
                        uploader,
                        image.lastModified()
                ));
            }
        }
    }

    private static Map<String, String> loadCaptions(String section, String year) {
        Map<String, String> captions = new HashMap<String, String>();
        File captionFile = photosYearPath(section, year).resolve("captions.properties").toFile();
        if (!captionFile.exists()) {
            return captions;
        }

        InputStream inputStream = null;
        try {
            inputStream = Files.newInputStream(captionFile.toPath());
            Properties properties = new Properties();
            properties.load(inputStream);
            for (String key : properties.stringPropertyNames()) {
                captions.put(key, properties.getProperty(key));
            }
        } catch (IOException ignored) {
            return captions;
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException ignored) {
                }
            }
        }

        return captions;
    }

    private static String resolveCaption(Map<String, String> captions, String fileName) {
        String exact = captions.get(fileName);
        if (exact != null) {
            return exact;
        }

        for (Map.Entry<String, String> entry : captions.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(fileName)) {
                return valueOrEmpty(entry.getValue());
            }
        }

        return "";
    }

    private static String resolveUploader(Map<String, String> captions, String fileName) {
        String exact = captions.get(uploaderKey(fileName));
        if (exact != null) {
            return valueOrEmpty(exact).trim().isEmpty() ? "Private user" : valueOrEmpty(exact).trim();
        }

        for (Map.Entry<String, String> entry : captions.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(uploaderKey(fileName))) {
                String name = valueOrEmpty(entry.getValue()).trim();
                return name.isEmpty() ? "Private user" : name;
            }
        }

        return "Private user";
    }

    private static List<String> listYearFolders(String section) {
        File folder = photosSectionPath(section).toFile();
        List<String> years = new ArrayList<String>();
        File[] files = folder.listFiles();
        if (files == null) {
            return years;
        }

        for (File file : files) {
            if (file.isDirectory()) {
                years.add(file.getName());
            }
        }

        Collections.sort(years, Collections.reverseOrder());
        return years;
    }

    private static List<File> listImages(String section, String year) {
        File folder = photosYearPath(section, year).toFile();
        List<File> images = new ArrayList<File>();
        File[] files = folder.listFiles();
        if (files == null) {
            return images;
        }

        for (File file : files) {
            if (file.isFile() && isImageFile(file.getName())) {
                images.add(file);
            }
        }

        Collections.sort(images, new Comparator<File>() {
            public int compare(File left, File right) {
                return left.getName().compareToIgnoreCase(right.getName());
            }
        });

        return images;
    }

    private static int countImages(String section, String year) {
        return listImages(section, year).size();
    }

    private static boolean isImageFile(String name) {
        String lower = name.toLowerCase();
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") || lower.endsWith(".svg") || lower.endsWith(".gif") || lower.endsWith(".webp");
    }

    private static String sanitizeSection(String section) {
        if ("birthdays".equals(section) || "trips".equals(section) || "plans".equals(section)) {
            return section;
        }
        return "";
    }

    private static String sanitizeYear(String year) {
        if (year == null) {
            return "";
        }
        return year.replaceAll("[^0-9A-Za-z_-]", "");
    }

    private static String sanitizeFileName(String fileName) {
        if (fileName == null) {
            return "";
        }
        String normalized = fileName.replace("\\", "/");
        int slashIndex = normalized.lastIndexOf('/');
        String baseName = slashIndex >= 0 ? normalized.substring(slashIndex + 1) : normalized;
        return baseName.replaceAll("[^0-9A-Za-z._-]", "_");
    }

    private static String extractBoundary(String contentType) {
        String[] parts = contentType.split(";");
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.startsWith("boundary=")) {
                return stripQuotes(trimmed.substring(9));
            }
        }
        return "";
    }

    private static void updateCaptionFile(String section, String year, String fileName, String caption, String uploader) throws IOException {
        java.nio.file.Path captionPath = photosYearPath(section, year).resolve("captions.properties");
        Properties properties = new Properties();
        if (Files.exists(captionPath)) {
            InputStream inputStream = null;
            try {
                inputStream = Files.newInputStream(captionPath);
                properties.load(inputStream);
            } finally {
                if (inputStream != null) {
                    inputStream.close();
                }
            }
        }

        properties.setProperty(fileName, valueOrEmpty(caption));
        properties.setProperty(uploaderKey(fileName), valueOrEmpty(uploader));
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        properties.store(outputStream, "Gallery captions");
        Files.write(captionPath, outputStream.toByteArray());
    }

    private static void removeCaptionEntry(String section, String year, String fileName) throws IOException {
        java.nio.file.Path captionPath = photosYearPath(section, year).resolve("captions.properties");
        if (!Files.exists(captionPath)) {
            return;
        }

        Properties properties = new Properties();
        InputStream inputStream = null;
        try {
            inputStream = Files.newInputStream(captionPath);
            properties.load(inputStream);
        } finally {
            if (inputStream != null) {
                inputStream.close();
            }
        }

        String matchedKey = null;
        for (String key : properties.stringPropertyNames()) {
            if (key.equalsIgnoreCase(fileName)) {
                matchedKey = key;
                break;
            }
        }

        if (matchedKey != null) {
            properties.remove(matchedKey);
            properties.remove(uploaderKey(matchedKey));
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            properties.store(outputStream, "Gallery captions");
            Files.write(captionPath, outputStream.toByteArray());
        }
    }

    private static String uploaderKey(String fileName) {
        return fileName + ".by";
    }

    private static String prettyName(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        String base = dotIndex > 0 ? fileName.substring(0, dotIndex) : fileName;
        return base.replace('-', ' ').replace('_', ' ');
    }

    private static java.nio.file.Path photosRootPath() {
        java.nio.file.Path localPath = Paths.get("src", "main", "resources", "static", "assets", "photos");
        if (Files.exists(localPath)) {
            return localPath;
        }
        java.nio.file.Path classesPath = Paths.get("target", "classes", "static", "assets", "photos");
        if (Files.exists(classesPath)) {
            return classesPath;
        }
        return localPath;
    }

    private static java.nio.file.Path photosSectionPath(String section) {
        return photosRootPath().resolve(section);
    }

    private static java.nio.file.Path photosYearPath(String section, String year) {
        return photosSectionPath(section).resolve(year);
    }

    private static String formatDate(long value) {
        return new SimpleDateFormat("dd MMM yyyy").format(new Date(value));
    }

    private static String readResource(String path) throws IOException {
        return new String(readResourceBytes(path), UTF_8);
    }

    private static byte[] readResourceBytes(String path) throws IOException {
        InputStream inputStream = OurStorySiteApplication.class.getResourceAsStream(path);
        if (inputStream != null) {
            try {
                return readAll(inputStream);
            } finally {
                inputStream.close();
            }
        }

        String cleanPath = path.startsWith("/") ? path.substring(1) : path;
        java.nio.file.Path fallbackPath = Paths.get("src", "main", "resources", cleanPath);
        if (Files.exists(fallbackPath)) {
            return Files.readAllBytes(fallbackPath);
        }
        java.nio.file.Path fallbackClassesPath = Paths.get("target", "classes", cleanPath);
        if (Files.exists(fallbackClassesPath)) {
            return Files.readAllBytes(fallbackClassesPath);
        }

        throw new IOException("Missing resource: " + path);
    }

    private static String contentType(String path) {
        if (path.endsWith(".css")) {
            return "text/css; charset=UTF-8";
        }
        if (path.endsWith(".js")) {
            return "application/javascript; charset=UTF-8";
        }
        if (path.endsWith(".properties")) {
            return "text/plain; charset=UTF-8";
        }
        if (path.endsWith(".svg")) {
            return "image/svg+xml; charset=UTF-8";
        }
        if (path.endsWith(".png")) {
            return "image/png";
        }
        if (path.endsWith(".jpg") || path.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        return "application/octet-stream";
    }

    private static byte[] readAll(InputStream inputStream) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, read);
        }
        return outputStream.toByteArray();
    }

    private static String joinStrings(List<String> values, String delimiter) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                builder.append(delimiter);
            }
            builder.append(values.get(i));
        }
        return builder.toString();
    }

    private static String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private static boolean isHemanth(String username) {
        return username != null && username.equalsIgnoreCase("Hemanth");
    }

    private static String escapeHtml(String input) {
        if (input == null) {
            return "";
        }
        return input
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private static class MemoryCard {
        private final String title;
        private final String description;
        private final String meta;
        private final String tag;

        private MemoryCard(String title, String description, String meta, String tag) {
            this.title = title;
            this.description = description;
            this.meta = meta;
            this.tag = tag;
        }

        private String getTitle() {
            return title;
        }

        private String getDescription() {
            return description;
        }

        private String getMeta() {
            return meta;
        }

        private String getTag() {
            return tag;
        }
    }

    private static class MultipartItem {
        private final String name;
        private final String fileName;
        private final byte[] data;

        private MultipartItem(String name, String fileName, byte[] data) {
            this.name = name;
            this.fileName = fileName;
            this.data = data;
        }

        private String getName() {
            return name;
        }

        private String getFileName() {
            return fileName;
        }

        private byte[] getData() {
            return data;
        }

        private String asText() {
            return new String(data, UTF_8).trim();
        }
    }

    private static class FeedItem {
        private final String section;
        private final String sectionLabel;
        private final String year;
        private final String fileName;
        private final String imageUrl;
        private final String yearUrl;
        private final String caption;
        private final String uploader;
        private final long lastModified;

        private FeedItem(String section, String sectionLabel, String year, String fileName, String imageUrl, String yearUrl, String caption, String uploader, long lastModified) {
            this.section = section;
            this.sectionLabel = sectionLabel;
            this.year = year;
            this.fileName = fileName;
            this.imageUrl = imageUrl;
            this.yearUrl = yearUrl;
            this.caption = caption;
            this.uploader = uploader;
            this.lastModified = lastModified;
        }

        private String getSection() {
            return section;
        }

        private String getSectionLabel() {
            return sectionLabel;
        }

        private String getYear() {
            return year;
        }

        private String getFileName() {
            return fileName;
        }

        private String getImageUrl() {
            return imageUrl;
        }

        private String getYearUrl() {
            return yearUrl;
        }

        private String getCaption() {
            return caption;
        }

        private String getUploader() {
            return uploader;
        }

        private long getLastModified() {
            return lastModified;
        }

        private String getDateLabel() {
            return formatDate(lastModified);
        }
    }

    private static class TemplateEngine {
        private TemplateEngine() {
        }

        private static String render(String template, Map<String, String> model) {
            String output = template;
            for (Map.Entry<String, String> entry : model.entrySet()) {
                output = output.replace("{{" + entry.getKey() + "}}", entry.getValue());
            }
            return output.replaceAll("\\{\\{[a-zA-Z0-9]+}}", "");
        }

        private static String wrap(String title, String body) throws IOException {
            Map<String, String> model = new HashMap<String, String>();
            model.put("title", title);
            model.put("body", body);
            return render(readResource("/templates/fallback.html"), model);
        }
    }
}
