package be.nabu.jfx.control.ace;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ForkJoinPool;

import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.concurrent.Worker.State;
import javafx.event.ActionEvent;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DataFormat;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.web.WebView;
import netscape.javascript.JSObject;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

//http://blog.mirkosertic.de/javastuff/javafxluaeditor
//https://github.com/ajaxorg/ace-builds
public class AceEditor {
	
	private static Boolean ENABLE_EMMET = Boolean.parseBoolean(System.getProperty("ace.emmet", "false"));
	
	public enum AceTheme {
		DEFAULT(null),
		TWILIGHT("ace/theme/twilight"),
		MONOKAI_DARK("ace/theme/monokai-dark")
		;
		private String path;

		private AceTheme(String path) {
			this.path = path;
		}

		public String getPath() {
			return path;
		}
	}
	
	public static final String COPY = "copy";
	public static final String PASTE = "paste";
	public static final String SAVE = "save";
	public static final String CLOSE = "close";
	public static final String CLOSE_ALL = "closeAll";
	public static final String CHANGE = "change";
	public static final String FULL_SCREEN = "fullScreen";
	
	private WebView webview;
	private boolean loaded;
	private String contentType;
	private String content;
	private Map<String, KeyCombination> keys = new LinkedHashMap<String, KeyCombination>();
	private Map<String, List<EventHandler<Event>>> handlers = new HashMap<String, List<EventHandler<Event>>>();
	private Map<String, String> modes = new HashMap<String, String>();
	private boolean keyPressed = false;
	
	private Map<String, Object> options = new HashMap<String, Object>();
	private Map<String, List<String>> startsWith = new HashMap<String, List<String>>();
	private Map<String, List<String>> contains = new HashMap<String, List<String>>();
	private AceTheme theme;
	
	public void trigger(String name) {
		Event event = new ActionEvent();
		List<EventHandler<Event>> list = handlers.get(name);
		if (list != null && !list.isEmpty()) {
			for (EventHandler<Event> handler : list) {
				handler.handle(event);
				if (event.isConsumed()) {
					break;
				}
			}
		}
	}
	
	public AceEditor() {
		setKeyCombination(COPY, new KeyCodeCombination(KeyCode.C, KeyCombination.CONTROL_DOWN));
		setKeyCombination(PASTE, new KeyCodeCombination(KeyCode.V, KeyCombination.CONTROL_DOWN));
		setKeyCombination(SAVE, new KeyCodeCombination(KeyCode.S, KeyCombination.CONTROL_DOWN));
		setKeyCombination(CLOSE_ALL, new KeyCodeCombination(KeyCode.W, KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN));
		setKeyCombination(CLOSE, new KeyCodeCombination(KeyCode.W, KeyCombination.CONTROL_DOWN));
		setKeyCombination(FULL_SCREEN, new KeyCodeCombination(KeyCode.F11));
//		subscribe(COPY, new CopyHandler(this));
//		subscribe(PASTE, new PasteHandler(this));
		// make sure it doesn't bubble up to the parent
		subscribe(SAVE, new ConsumeHandler());
		subscribe(CLOSE, new ConsumeHandler());
		// set the modes
		setMode("application/javascript", "javascript");
		setMode("application/x-javascript", "javascript");
		setMode("text/html", "html");
		setMode("text/xml", "xml");
		setMode("application/xml", "xml");
		setMode("text/x-glue", "python");
		setMode("text/x-eglue", "python");
		setMode("text/x-gcss", "css");
		setMode("text/css", "css");
		setMode("text/x-markdown", "markdown");
		setMode("application/x-sql", "sql");
		setMode("text/sql", "sql");
		setMode("text/x-template", "html");
		setMode("text/x-sass", "sass");
		setMode("text/x-scss", "scss");
		
//		setTheme(AceTheme.MONOKAI_DARK);
		
		if (ENABLE_EMMET) {
			setEmmet(true);
		}
	}
	public void setWrap(boolean wrapping) {
		setOption("wrap", wrapping);
	}
	public void requestFocus() {
		getWebView().requestFocus();
		if (loaded) {
			getWebView().getEngine().executeScript("editorFocus()");
		}
	}
	public void setShowWhitespace(boolean showWhitespace) {
		setOption("showInvisibles", showWhitespace);
	}
	public void setEmmet(boolean enableEmmet) {
		setOption("enableEmmet", enableEmmet);
	}
	public void setLiveAutocompletion(boolean autocompletion) {
		setOption("enableLiveAutocompletion", autocompletion);
	}
	public void setTabSize(int tabSize) {
		setOption("tabSize", tabSize);
	}
	public void setSoftTabs(boolean softTabs) {
		setOption("useSoftTabs", softTabs);
	}
	public void setReadOnly(boolean readOnly) {
		setOption("readOnly", readOnly);
	}
	public void setTheme(AceTheme theme) {
		if (theme.getPath() != null) {
			setOption("theme", '"' + theme.getPath() + '"');
		}
		this.theme = theme;
	}
	private void setOption(String key, Object value) {
		options.put(key, value);
		if (loaded) {
			getWebView().getEngine().executeScript("setOption('" + key + "', " + value + ")");
		}
	}
	public void setMode(String contentType, String mode) {
		modes.put(contentType, mode);
	}
	public void setKeyCombination(String String, KeyCombination combination) {
		if (String == CHANGE) {
			throw new IllegalArgumentException("Can not set a key combination on change, it is the absence of a key combination");
		}
		if (combination == null) {
			this.keys.remove(String);
		}
		else {
			this.keys.put(String, combination);
		}
	}
	
	public void subscribe(String String, EventHandler<Event> handler) {
		if (!handlers.containsKey(String)) {
			handlers.put(String, new ArrayList<EventHandler<Event>>());
		}
		handlers.get(String).add(0, handler);
	}
	
	private String getMatch(KeyEvent event) {
		for (String string : this.keys.keySet()) {
			if (this.keys.get(string).match(event)) {
				return string;
			}
		}
		return null;
	}
	
	public void append(String content) {
		if (loaded) {
			JSObject window = (JSObject) getWebView().getEngine().executeScript("window");
			window.call("pasteValue", content);
		}
	}
	
	public void addStartsWith(String name, List<String> values) {
		if (loaded) {
			JSObject window = (JSObject) getWebView().getEngine().executeScript("window");
			window.call("setStartsWithCompletions", name, values.toArray(new String[values.size()]));
		}
		else {
			startsWith.put(name, values);
		}
	}

	public void addContains(String name, List<String> values) {
		if (loaded) {
			JSObject window = (JSObject) getWebView().getEngine().executeScript("window");
			window.call("setContainsCompletions", name, values.toArray(new String[values.size()]));
		}
		else {
			contains.put(name, values);
		}
	}
	
	public WebView getWebView() {
		if (this.webview == null) {
			synchronized(this) {
				if (this.webview == null) {
					webview = new WebView();
					webview.getEngine().setJavaScriptEnabled(true);

					// we provide external menu stuff, the internal ace menu doesn't work too well anyway because copy/paste etc is restricted within javascript
					webview.setContextMenuEnabled(false);

					String externalForm = AceEditor.class.getResource("/ace/editor.html").toExternalForm();
					System.out.println("Found editor: " + externalForm);

					webview.getEngine().getLoadWorker().stateProperty().addListener(new ChangeListener<State>() {
						@Override
						public void changed(ObservableValue<? extends State> observable, State oldValue, State newValue) {
							if (newValue == State.SUCCEEDED) {
//								loaded = true;
//								if (content != null) {
//									setContentInWebview(contentType, content);
//									contentType = null;
//									content = null;
//								}
								
//								try {
//									JSObject window = (JSObject) webview.getEngine().executeScript("window");
//								    window.setMember("java", new JavaBridge());
//								    webview.getEngine().executeScript("console.log = function(message) { java.log(message); };");
//								}
//								catch (Exception e) {
//									e.printStackTrace();
//								}
							}
						}
					});

					webview.getEngine().documentProperty().addListener(new ChangeListener<Document>() {
						@Override
						public void changed(ObservableValue<? extends Document> arg0, Document arg1, Document arg2) {
							loaded = true;
							if (content != null) {
								setContentInWebview(contentType, content);
								contentType = null;
								content = null;
								for (String key : options.keySet()) {
									setOption(key, options.get(key));
								}
							}
							else {
								getWebView().getEngine().executeScript("initEditor()");
							}
							for (String key : startsWith.keySet()) {
								addStartsWith(key, startsWith.get(key));
							}
							for (String key : contains.keySet()) {
								addContains(key, contains.get(key));
							}
							JSObject window = (JSObject) webview.getEngine().executeScript("window");
						    window.setMember("java", bridge);
						    webview.getEngine().executeScript("console.log = function(message) { java.log(message); };");
						}
					});

					webview.getEngine().load(externalForm);
					
					webview.addEventHandler(KeyEvent.KEY_PRESSED, new EventHandler<KeyEvent>() {
						@Override
						public void handle(KeyEvent event) {
							keyPressed = true;
							String match = getMatch(event);
							// if no predefined elements were found, use the change String
							if (match != null) {
								List<EventHandler<Event>> list = handlers.get(match);
								if (list != null && !list.isEmpty()) {
									for (EventHandler<Event> handler : list) {
										handler.handle(event);
										if (event.isConsumed()) {
											break;
										}
									}
								}
							}
						}
					});
					webview.addEventHandler(KeyEvent.KEY_RELEASED, new EventHandler<KeyEvent>() {
						@Override
						public void handle(KeyEvent arg0) {
							keyPressed = false;
						}
					});
					webview.addEventHandler(KeyEvent.KEY_TYPED, new EventHandler<KeyEvent>() {
						@Override
						public void handle(KeyEvent event) {
							if (keyPressed && event.getCharacter() != null && !event.getCharacter().isEmpty() && !event.isControlDown()) {
								// run asynchronously, we want to capture the last added content as well
								Platform.runLater(new Runnable() {
									@Override
									public void run() {
										fireChanged(event);
									}
								});
							}
						}

					});
					return webview;
				}
			}
		}
		return webview;
	}
	
	private void fireChanged(Event event) {
		List<EventHandler<Event>> list = handlers.get(CHANGE);
		if (list != null && !list.isEmpty()) {
			for (EventHandler<Event> handler : list) {
				handler.handle(event);
				if (event.isConsumed()) {
					break;
				}
			}
		}
	}
	
	public String getSelection() {
		return (String) getWebView().getEngine().executeScript("copySelection()");
	}
	
	public String getContent() {
		return (String) webview.getEngine().executeScript("getValue()");
	}
	
	public void setContent(String contentType, String content) {
		if (loaded) {
			setContentInWebview(contentType, content);
		}
		else {
			this.contentType = contentType;
			this.content = content;
		}
	}

	public static class CopyHandler implements EventHandler<Event> {
		
		private AceEditor editor;

		public CopyHandler(AceEditor editor) {
			this.editor = editor;
		}
		
		@Override
		public void handle(Event event) {
			final String selection = (String) editor.getWebView().getEngine().executeScript("copySelection()");
			// run with a delay, somewhere between 8_150 and 8_211 the copy functionality as it was before (without the copy handler at all) worked like a charm
			// ace is also reporting the correct text for copying so presumable the webview itself is doing something off
			System.out.println("copying selection: " + selection);
			ClipboardContent clipboardContent = new ClipboardContent();
			clipboardContent.put(DataFormat.PLAIN_TEXT, selection);
//						clipboardContent.putString(selection);
			Clipboard.getSystemClipboard().setContent(clipboardContent);
			event.consume();
		}
	}
	
	public static class PasteHandler implements EventHandler<Event> {
		
		private AceEditor editor;

		public PasteHandler(AceEditor editor) {
			this.editor = editor;
		}
		
		@Override
		public void handle(Event event) {
			Clipboard clipboard = Clipboard.getSystemClipboard();
			String content = (String) clipboard.getContent(DataFormat.PLAIN_TEXT);
			if (content != null) {
				JSObject window = (JSObject) editor.getWebView().getEngine().executeScript("window");
				window.call("pasteValue", content);
			}
			event.consume();
			editor.fireChanged(event);
		}
	}
	
	public static class ConsumeHandler implements EventHandler<Event> {
		@Override
		public void handle(Event event) {
			event.consume();
		}
	}
	
	private void setContentInWebview(String contentType, String content) {
		getWebView().getEngine().executeScript("resetDiv()");
		// set the content
		Document document = webview.getEngine().getDocument();
		Element editor = document.getElementById("editor");
		while(editor == null) {
			try {
				Thread.sleep(10);
			}
			catch (InterruptedException e) {
				break;
			}
			editor = document.getElementById("editor");
		}
		editor.setTextContent(content);
		// initialize editor
		getWebView().getEngine().executeScript("initEditor()");
		if (theme != null && theme.getPath() != null) {
			System.out.println("Setting theme: " + theme.getPath());
			getWebView().getEngine().executeScript("editor.setTheme('" + theme.getPath() + "');");
		}
		String mode = modes.get(contentType);
		if (mode != null) {
			getWebView().getEngine().executeScript("editor.getSession().setMode('ace/mode/" + mode + "');");
		}
	}

	private final JavaBridge bridge = new JavaBridge();
	
	public class JavaBridge {
		public void log(String text) {
			System.out.println(text);
		}
		// seriously nasty workaround for a bug (?) introduced somewhere between 8_150 and 8_212
		// the copy was working splendidly before that but somewhere in that version range the copy broke
		// javascript sees the correct value-to-be-copied, java also sees the correct value but somehow, someway the clipboard is empty when you copy a value from the ace editor
		// fun fact: if you do ctrl+f in ace you get an inline find popup, the copy/paste still worked there without any problems
		// it seems that the copy from the webview (or webkit) fails to detect the currently selected text and simply copies an empty string into the clipboard
		// there seems to be no way to preventDefault or stopPropagation the copy event itself meaning the webview clipboard version always wins
		// unless...we do a nasty timeout like this
		public void copy(final String selection) {
			ForkJoinPool.commonPool().submit(new Runnable() {

				@Override
				public void run() {
					try {
						Thread.sleep(100);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
					Platform.runLater(new Runnable() {
						@Override
						public void run() {
							ClipboardContent clipboardContent = new ClipboardContent();
							clipboardContent.put(DataFormat.PLAIN_TEXT, selection);
							Clipboard.getSystemClipboard().setContent(clipboardContent);
						}
					});
				}
			});
		}
	}

}
