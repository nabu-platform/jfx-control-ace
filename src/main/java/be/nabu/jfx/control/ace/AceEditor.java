package be.nabu.jfx.control.ace;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.concurrent.Worker.State;
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
	
	public static final String COPY = "copy";
	public static final String PASTE = "paste";
	public static final String SAVE = "save";
	public static final String CLOSE = "close";
	public static final String CLOSE_ALL = "closeAll";
	public static final String CHANGE = "CHANGE";
	
	private WebView webview;
	private boolean loaded;
	private String contentType;
	private String content;
	private Map<String, KeyCombination> keys = new LinkedHashMap<String, KeyCombination>();
	private Map<String, List<EventHandler<Event>>> handlers = new HashMap<String, List<EventHandler<Event>>>();
	private Map<String, String> modes = new HashMap<String, String>();
	private boolean keyPressed = false;
	
	private Map<String, Object> options = new HashMap<String, Object>();
	
	public AceEditor() {
		setKeyCombination(COPY, new KeyCodeCombination(KeyCode.C, KeyCombination.CONTROL_DOWN));
		setKeyCombination(PASTE, new KeyCodeCombination(KeyCode.V, KeyCombination.CONTROL_DOWN));
		setKeyCombination(SAVE, new KeyCodeCombination(KeyCode.S, KeyCombination.CONTROL_DOWN));
		setKeyCombination(CLOSE_ALL, new KeyCodeCombination(KeyCode.W, KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN));
		setKeyCombination(CLOSE, new KeyCodeCombination(KeyCode.W, KeyCombination.CONTROL_DOWN));
		subscribe(COPY, new CopyHandler(this));
		subscribe(PASTE, new PasteHandler(this));
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
		for (String String : this.keys.keySet()) {
			if (this.keys.get(String).match(event)) {
				return String;
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
					webview.getEngine().load(externalForm);

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
						}
					});

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
						}
					});
					return webview;
				}
			}
		}
		return webview;
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
			String selection = (String) editor.getWebView().getEngine().executeScript("copySelection()");
			Clipboard clipboard = Clipboard.getSystemClipboard();
			ClipboardContent clipboardContent = new ClipboardContent();
			clipboardContent.putString(selection);
			clipboard.setContent(clipboardContent);
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
		String mode = modes.get(contentType);
		if (mode != null) {
			getWebView().getEngine().executeScript("editor.getSession().setMode('ace/mode/" + mode + "');");
		}
	}
}
