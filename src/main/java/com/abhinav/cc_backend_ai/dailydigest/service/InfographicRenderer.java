package com.abhinav.cc_backend_ai.dailydigest.service;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import javax.imageio.ImageIO;

import org.springframework.stereotype.Service;

import com.abhinav.cc_backend_ai.dailydigest.model.NewsDigest;
import com.abhinav.cc_backend_ai.dailydigest.model.NewsStory;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.ScreenshotType;
import com.microsoft.playwright.options.WaitUntilState;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class InfographicRenderer {

	private static final List<String> ORDER = List.of("World", "India", "Delhi NCR", "Chandigarh");

	private static final Map<String, String> COLORS = Map.of("World", "world", "India", "india", "Delhi NCR", "delhi",
			"Chandigarh", "chandigarh");

	public byte[] render(NewsDigest digest, String digestDate) throws Exception {
		log.info("Render start");

		StringBuilder sections = new StringBuilder();
		int number = 0;

		for (String category : ORDER) {
			List<Integer> indexes = new ArrayList<>();

			for (int i = 0; i < digest.stories().size(); i++) {
				if (category.equalsIgnoreCase(digest.stories().get(i).category())) {
					indexes.add(i);
				}
			}

			if (indexes.isEmpty()) {
				continue;
			}

			String cssClass = css(category);

			sections.append("<section class='group ").append(cssClass).append("'>")
					.append("<div class='section-title'>").append("<span class='dot'></span>")
					.append(esc(category.toUpperCase(Locale.ROOT))).append("<span class='section-line'></span>")
					.append("</div><div class='grid'>");

			for (int i : indexes) {
				NewsStory story = digest.stories().get(i);
				number++;

				String img = imageDataUri(story.imagePath());

				sections.append("<article class='card'>").append("<div class='number'>").append(number)
						.append("</div><div class='photo'>");

				if (!img.isEmpty()) {
					sections.append("<img src='").append(esc(img)).append("' alt='").append(esc(story.headline()))
							.append("'>");
				} else {
					sections.append("<div class='placeholder'>").append(esc(category.toUpperCase(Locale.ROOT)))
							.append("</div>");
				}

				sections.append("</div><div class='copy'>").append("<h2>").append(esc(story.headline()))
						.append("</h2><p>").append(esc(story.summary())).append("</p><div class='meta'>")
						.append("<span>")
						.append(esc(
								sourceLabel(story.category()) != null ? sourceLabel(story.category()) : story.source()))
						.append("</span><span>").append(esc(story.publishedDate()))
						.append("</span></div></div></article>");
			}

			sections.append("</div></section>");
		}

		return createInfographicSafely(digest, sections, digestDate);
	}

	private byte[] createInfographic(NewsDigest digest, StringBuilder sections, String digestDate) throws IOException {

		String html = """
				<!doctype html>
				<html>
				<head>
				  <meta charset="utf-8">
				  <meta name="viewport" content="width=device-width, initial-scale=1">

				  <style>
				    * {
				      box-sizing: border-box;
				    }

				    html, body {
				      margin: 0;
				      padding: 0;
				      background: #edf2f8;
				      color: #10264b;
				      font-family: Arial, Helvetica, sans-serif;
				    }

				    body {
				      width: 390px;
				      margin: 0 auto;
				    }

				    .page {
				      width: 100%;
				      padding: 10px;
				      background: #ffffff;
				    }

				    header {
				      position: relative;
				      overflow: hidden;
				      padding: 22px 18px 16px;
				      color: #ffffff;
				      border-radius: 12px;
				      background: linear-gradient(115deg, #071d43, #06447b);
				    }

				    header:after {
				      content: "◉";
				      position: absolute;
				      right: 18px;
				      top: -28px;
				      font-size: 125px;
				      color: #27bdf0;
				      opacity: 0.18;
				    }

				    .eyebrow {
				      position: relative;
				      z-index: 1;
				      font-size: 11px;
				      font-weight: bold;
				      letter-spacing: 2px;
				      color: #7ce0ff;
				    }

				    h1 {
				      position: relative;
				      z-index: 1;
				      font-size: 32px;
				      line-height: 1.1;
				      margin: 10px 0;
				      letter-spacing: 0.5px;
				    }

				    h1 span {
				      color: #2ac4f5;
				    }

				    .date {
				      position: relative;
				      z-index: 1;
				      font-size: 14px;
				      margin-bottom: 14px;
				    }

				    .coverage {
				      position: relative;
				      z-index: 1;
				      border-top: 1px solid #3671a7;
				      padding-top: 10px;
				      color: #d3e7ff;
				      font-size: 10px;
				      font-weight: bold;
				      letter-spacing: 0.5px;
				      line-height: 1.7;
				    }

				    .group {
				      margin-top: 18px;
				    }

				    .section-title {
				      display: flex;
				      align-items: center;
				      gap: 9px;
				      padding-bottom: 8px;
				      margin-bottom: 10px;
				      border-bottom: 2px solid #dbe7f5;
				      font-size: 17px;
				      font-weight: 800;
				      letter-spacing: 0.5px;
				    }

				    .dot {
				      width: 10px;
				      height: 10px;
				      border-radius: 50%;
				      flex-shrink: 0;
				      background: #0b63bd;
				    }

				    .section-line {
				      flex: 1;
				      height: 2px;
				      background: #dbe7f5;
				    }

				    .world .section-title { color: #0b63bd; }
				    .world .dot, .world .number { background: #0b63bd; }
				    .world .section-line { background: #0b63bd; }

				    .india .section-title { color: #e87900; }
				    .india .dot, .india .number { background: #e87900; }
				    .india .section-line { background: #e87900; }

				    .delhi .section-title { color: #008b7f; }
				    .delhi .dot, .delhi .number { background: #008b7f; }
				    .delhi .section-line { background: #008b7f; }

				    .chandigarh .section-title { color: #7950c8; }
				    .chandigarh .dot, .chandigarh .number { background: #7950c8; }
				    .chandigarh .section-line { background: #7950c8; }

				    .grid {
				      display: grid;
				      grid-template-columns: minmax(0, 1fr);
				      gap: 10px;
				      align-items: start;
				    }

				    .card {
				      position: relative;
				      display: grid;
				      grid-template-columns: 34% minmax(0, 1fr);
				      grid-template-rows: auto auto auto;
				      column-gap: 12px;
				      row-gap: 10px;
				      align-items: start;
				      min-width: 0;
				      height: auto;
				      padding: 12px;
				      border: 1px solid #d6e5f5;
				      border-radius: 10px;
				      background: #fbfdff;
				      break-inside: avoid;
				    }

				    .number {
				      position: absolute;
				      z-index: 2;
				      top: 5px;
				      left: 5px;
				      display: flex;
				      align-items: center;
				      justify-content: center;
				      width: 25px;
				      height: 25px;
				      border: 2px solid #ffffff;
				      border-radius: 50%;
				      color: #ffffff;
				      font-size: 13px;
				      font-weight: bold;
				    }

				    .photo {
				      grid-column: 1;
				      grid-row: 1;
				      width: 100%;
				      min-width: 0;
				      aspect-ratio: 4 / 3;
				      overflow: hidden;
				      border-radius: 7px;
				      background: #e7eef7;
				    }

				    .photo img {
				      display: block;
				      width: 100%;
				      height: 100%;
				      object-fit: cover;
				      object-position: center;
				    }

				    .placeholder {
				      display: flex;
				      align-items: center;
				      justify-content: center;
				      width: 100%;
				      height: 100%;
				      min-height: 85px;
				      padding: 8px;
				      color: #55708f;
				      text-align: center;
				      font-size: 10px;
				      font-weight: bold;
				      letter-spacing: 0.5px;
				    }

				    .copy {
				      display: contents;
				    }

				    .card h2 {
				      grid-column: 2;
				      grid-row: 1;
				      align-self: center;
				      min-width: 0;
				      margin: 0;
				      color: #092b60;
				      font-size: 16px;
				      line-height: 1.3;
				      font-weight: 700;
				      overflow-wrap: anywhere;
				    }

				    .card .copy p {
				      grid-column: 1 / -1;
				      grid-row: 2;
				      min-width: 0;
				      margin: 0;
				      color: #354b6b;
				      font-size: 13px;
				      line-height: 1.45;
				      overflow-wrap: anywhere;
				    }

				    .card .meta {
				      grid-column: 1 / -1;
				      grid-row: 3;
				      display: flex;
				      flex-wrap: wrap;
				      gap: 4px 8px;
				      min-width: 0;
				      margin: 0;
				      color: #62748b;
				      font-size: 10px;
				      line-height: 1.35;
				      overflow-wrap: anywhere;
				    }

				    footer {
				      display: flex;
				      align-items: flex-start;
				      flex-direction: column;
				      gap: 9px;
				      margin-top: 20px;
				      padding: 14px;
				      border-radius: 10px;
				      background: #eaf7fd;
				      color: #143c72;
				      font-size: 11px;
				    }

				    footer strong {
				      font-size: 13px;
				    }

				    footer span {
				      color: #4b6584;
				    }

				    .tagline {
				      font-size: 10px;
				      font-weight: bold;
				      letter-spacing: 0.7px;
				      text-align: left;
				    }
				  </style>
				</head>

				<body>
				  <div class="page">
				    <header>
				      <div class="eyebrow">YOUR DAILY NEWS BRIEFING @ 8·2·8</div>
				      <h1>THE DAILY <span>20</span></h1>
				      <div class="date">__DIGEST_DATE__</div>
				      <div class="coverage">
				        WORLD   •   INDIA   •   DELHI NCR   •   CHANDIGARH
				      </div>
				    </header>

				    __DIGEST_SECTIONS__

				    <footer>
				      <div>
				        <strong>THE DAILY 20</strong><br>
				        <span>Trusted sources · Key updates · One briefing</span>
				      </div>
				      <div class="tagline">STAY INFORMED. STAY AHEAD.</div>
				    </footer>
				  </div>
				</body>
				</html>
				""";

		html = html.replace("__DIGEST_DATE__", esc(digestDate)).replace("__DIGEST_SECTIONS__", sections.toString());
		log.info("RENDER: Before Playwright creation");
		logMemory("before Playwright creation");

		System.gc(); // Clear heap before launching browser to protect 512MB RAM limit

		try (Playwright p = Playwright.create()) {
			logMemory("after Playwright creation");
			log.info("RENDER: Playwright created");
			log.info("RENDER: Launching Chromium");

			Browser browser;
			try {
				browser = p.chromium()
						.launch(new BrowserType.LaunchOptions().setHeadless(true).setTimeout(120_000)
								.setArgs(List.of("--no-sandbox", "--disable-dev-shm-usage", "--disable-gpu",
										"--no-zygote", "--single-process")));
			} catch (Exception ex) {
				log.error("RENDER: Chromium launch failed", ex);
				throw ex;
			}
			logMemory("after Chromium launch");
			log.info("RENDER: Chromium launched");

			try {
				Page page = browser
						.newPage(new Browser.NewPageOptions().setViewportSize(390, 1000).setDeviceScaleFactor(2));

				page.setDefaultTimeout(90_000);
				page.setDefaultNavigationTimeout(90_000);

				log.info("RENDER: Page created");
				log.info("RENDER: Before HTML file generation");

				long loadStart = System.nanoTime();

				// Write HTML to a temp file to bypass Playwright IPC string pipe bottlenecks
				Path tempHtmlFile = Files.createTempFile("digest-", ".html");
				Files.writeString(tempHtmlFile, html);

				try {
					page.navigate("file://" + tempHtmlFile.toAbsolutePath().toString(), new Page.NavigateOptions()
							.setWaitUntil(WaitUntilState.COMMIT).setTimeout(120_000));
				} finally {
					try {
						Files.deleteIfExists(tempHtmlFile);
					} catch (Exception ignored) {
					}
				}

				log.info("RENDER: HTML loaded in {} ms", (System.nanoTime() - loadStart) / 1_000_000);

				log.info("RENDER: Waiting for embedded images");

				page.evaluate("""
						async () => {
						    const images = Array.from(document.images);

						    await Promise.race([
						        Promise.all(
						            images.map(img => {
						                if (img.complete) {
						                    return Promise.resolve();
						                }

						                return new Promise(resolve => {
						                    img.addEventListener("load", resolve, { once: true });
						                    img.addEventListener("error", resolve, { once: true });
						                });
						            })
						        ),
						        new Promise((_, reject) =>
						            setTimeout(
						                () => reject(new Error("Image loading timed out")),
						                15000
						            )
						        )
						    ]);

						    return images.length;
						}
						""");

				log.info("RENDER: Embedded image wait completed");

				log.info("RENDER: Waiting for fonts");

				page.evaluate("""
						    async () => {
						        await document.fonts.ready;
						    }
						""");

				log.info("RENDER: Fonts ready");

				// completely finish painting layout boxes under strict 0.1 vCPU throttling
				page.waitForTimeout(200);

				byte[] output = captureInFixedSlicesNew(page);

				log.info("RENDER: Final screenshot completed, size={} MB",
						String.format("%.2f", output.length / (1024.0 * 1024.0)));

				page.close();

				return output;

			} finally {
				try {
					if (browser.isConnected()) {
						browser.close();
					}
				} catch (Exception closeEx) {
					log.warn("RENDER: Error while closing Chromium", closeEx);
				} finally {
					System.gc(); // Clean up native memory blocks
					log.info("RENDER: Render cleanup finished");
				}
			}
		}
	}

	private String css(String category) {
		return COLORS.getOrDefault(category, "world");
	}

	private String esc(String value) {
		if (value == null)
			return "";
		return value.replace('&', '&').replace('<', '<').replace('>', '>').replace('"', '"').replace('\'', '\'');
	}

	private String imageDataUri(String imagePath) throws IOException {
		if (imagePath == null || imagePath.isBlank()) {
			return "";
		}

		Path path = Path.of(imagePath);

		if (!Files.isRegularFile(path)) {
			log.warn("Image file not found: {}", imagePath);
			return "";
		}

		long fileSize = Files.size(path);
		if (fileSize == 0 || fileSize > 10 * 1024 * 1024) {
			log.warn("Skipping image with invalid or excessive size: {} bytes", fileSize);
			return "";
		}

		BufferedImage original;
		try {
			original = ImageIO.read(path.toFile());
		} catch (Exception e) {
			log.warn("Failed to decode image due to color profile or corruption: {}", imagePath, e);
			return "";
		}

		if (original == null) {
			log.warn("Unsupported or unreadable image: {}", imagePath);
			return "";
		}

		try {
			int maxWidth = 300; // Optimized for memory footprint at 2x scaling
			int originalWidth = original.getWidth();
			int originalHeight = original.getHeight();

			double scale = Math.min(1.0, (double) maxWidth / originalWidth);

			int width = Math.max(1, (int) Math.round(originalWidth * scale));
			int height = Math.max(1, (int) Math.round(originalHeight * scale));

			BufferedImage resized = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

			java.awt.Graphics2D graphics = resized.createGraphics();

			try {
				graphics.setColor(java.awt.Color.WHITE);
				graphics.fillRect(0, 0, width, height);

				graphics.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
						java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);

				graphics.drawImage(original, 0, 0, width, height, null);
			} finally {
				graphics.dispose();
			}

			java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();

			if (!ImageIO.write(resized, "jpg", output)) {
				throw new IOException("No JPEG writer available");
			}

			byte[] compressedBytes = output.toByteArray();
			resized.flush();

			log.info("Image compressed: {}x{} -> {}x{}, {} KB", originalWidth, originalHeight, width, height,
					compressedBytes.length / 1024);

			return "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(compressedBytes);

		} finally {
			original.flush();
		}
	}

	private String sourceLabel(String category) {
		return switch (category) {
		case "World" -> "World News Headlines";
		case "Delhi NCR" -> "Delhi News";
		case "Chandigarh" -> "Chandigarh News";
		default -> null;
		};
	}

	private void logMemory(String stage) {
		Runtime runtime = Runtime.getRuntime();

		long usedMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);

		long maxMb = runtime.maxMemory() / (1024 * 1024);

		log.info("MEMORY [{}]: heapUsed={}MB, heapMax={}MB", stage, usedMb, maxMb);
	}

	private final ReentrantLock renderLock = new ReentrantLock();

	public byte[] createInfographicSafely(NewsDigest digest, StringBuilder sections, String digestDate)
			throws Exception {

		if (!renderLock.tryLock()) {
			throw new IllegalStateException("Another infographic render is already running");
		}

		try {
			return createInfographic(digest, sections, digestDate);
		} finally {
			renderLock.unlock();
		}
	}

	private byte[] captureInFixedSlicesNew(Page page) throws IOException {
	    final int viewportHeight = 1000;
	    final int width = 390;
	    final int scale = 2; // Matches your device scale factor

	    // 1. Get total document height normally via scrollHeight
	    Number heightValue = (Number) page.evaluate("() => document.documentElement.scrollHeight");
	    int documentHeight = heightValue.intValue();

	    if (documentHeight <= 0) {
	        throw new IOException("Invalid document height: " + documentHeight);
	    }

	    int outputWidth = width * scale;
	    int outputHeight = documentHeight * scale;

	    log.info("RENDER: Capturing {} x {} CSS pixels via strict non-overlapping slices", width, documentHeight);

	    BufferedImage stitched = new BufferedImage(outputWidth, outputHeight, BufferedImage.TYPE_INT_RGB);
	    Graphics2D graphics = stitched.createGraphics();

	    try {
	        int scrollY = 0;

	        while (scrollY < documentHeight) {
	            // Ensure we don't scroll past the max scroll position
	            int maxScrollY = Math.max(0, documentHeight - viewportHeight);
	            int currentScrollY = Math.min(scrollY, maxScrollY);

	            log.info("RENDER: Scrolling to y={}", currentScrollY);
	            page.evaluate("(y) => window.scrollTo(0, y)", currentScrollY);
	            page.waitForTimeout(150); // Settlement buffer

	            byte[] sliceBytes;
	            try {
	                sliceBytes = page.screenshot(new Page.ScreenshotOptions()
	                        .setType(ScreenshotType.PNG)
	                        .setFullPage(false)
	                        .setTimeout(120_000));
	            } catch (Exception e) {
	                log.error("RENDER: Slice failed at y={}", currentScrollY, e);
	                throw e;
	            }

	            BufferedImage slice = ImageIO.read(new ByteArrayInputStream(sliceBytes));
	            sliceBytes = null;

	            if (slice == null) {
	                throw new IOException("Unable to decode slice at y=" + currentScrollY);
	            }

	            try {
	                int sliceHeightCss = slice.getHeight() / scale;
	                
	                // If we are at the very bottom, calculate the exact remaining height to avoid duplicate pixels
	                int remainingHeightCss = documentHeight - currentScrollY;
	                int drawHeightCss = Math.min(sliceHeightCss, remainingHeightCss);

	                // Draw exact non-overlapping region onto the master canvas
	                graphics.drawImage(
	                        slice, 
	                        0, currentScrollY * scale, 
	                        outputWidth, (currentScrollY + drawHeightCss) * scale, 
	                        0, 0, 
	                        slice.getWidth(), drawHeightCss * scale, 
	                        null
	                );

	            } finally {
	                slice.flush();
	            }

	            System.gc();

	            // If we've reached or passed the max scroll position, we've captured the footer; break cleanly
	            if (currentScrollY >= maxScrollY) {
	                break;
	            }

	            scrollY += viewportHeight;
	        }

	        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
	            if (!ImageIO.write(stitched, "png", output)) {
	                throw new IOException("PNG encoder is unavailable");
	            }
	            return output.toByteArray();
	        }

	    } finally {
	        graphics.dispose();
	        stitched.flush();
	    }
	}
}