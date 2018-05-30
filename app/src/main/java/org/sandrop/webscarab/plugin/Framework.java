/***********************************************************************
 *
 * This file is part of SandroProxy, 
 * For details, please see http://code.google.com/p/sandrop/
 *
 * Copyright (c) 2012 supp.sandrob@gmail.com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 *
 * Getting Source
 * ==============
 *
 * Source for this application is maintained at
 * http://code.google.com/p/sandrop/
 *
 * Software is build from sources of WebScarab project
 * For details, please see http://www.sourceforge.net/projects/owasp
 *
 */
package org.sandrop.webscarab.plugin;

import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.sandrop.webscarab.httpclient.HTTPClientFactory;
import org.sandrop.webscarab.model.ConnectionDescriptor;
import org.sandrop.webscarab.model.ConversationID;
import org.sandrop.webscarab.model.FrameworkModel;
import org.sandrop.webscarab.model.Preferences;
import org.sandrop.webscarab.model.Request;
import org.sandrop.webscarab.model.Response;
import org.sandrop.webscarab.model.StoreException;
import org.sandrop.webscarab.plugin.fragments.Fragments;
import org.sandroproxy.utils.NetworkUtils;
import org.sandroproxy.utils.PreferenceUtils;

import EDU.oswego.cs.dl.util.concurrent.QueuedExecutor;
import EDU.oswego.cs.dl.util.concurrent.ThreadFactory;
import android.content.Context;


public class Framework {
    
    private ArrayList<Plugin> _plugins = new ArrayList<Plugin>();
    private final QueuedExecutor analysisQueuedExecutor;
    private final QueuedExecutor analysisLongRunningQueuedExecutor;
    
    private FrameworkModel _model;
    private FrameworkModelWrapper _wrapper;
    
    private Logger _logger = Logger.getLogger(getClass().getName());
    
    private String _version;
    
    private ScriptManager _scriptManager;
    private CredentialManager _credentialManager;
    
    private AddConversationHook _allowAddConversation;
    
    private AnalyseConversationHook _analyseConversation;
    
    private Pattern dropPattern = null;
    private Pattern whitelistPattern = null;
    
    private Context mContext;
    
    private boolean _captureData = false;
    
    
    /**
     * Creates a new instance of Framework
     */
    public Framework(Context context) {
        _logger.setLevel(Level.FINEST);
        mContext = context;
        
        // sandrop this is done in application class 
        // Preferences.init(mContext);
        
        _model = new FrameworkModel();
        _wrapper = new FrameworkModelWrapper(_model);
        // sandrop commented until used
        //_scriptManager = new ScriptManager(this);
        _allowAddConversation = new AddConversationHook();
        _analyseConversation = new AnalyseConversationHook();
        // commented until used
        //_scriptManager.registerHooks("Framework", new Hook[] { _allowAddConversation, _analyseConversation });
        extractVersionFromManifest();
        _credentialManager = new CredentialManager();

        if (Preferences.getPreferenceBoolean(PreferenceUtils.chainProxyEnabled, false)){
            String proxyCredentialsUsername = Preferences.getPreference(PreferenceUtils.chainProxyUsername, null);
            String proxyCredentialsPassword = Preferences.getPreference(PreferenceUtils.chainProxyPassword, null);
            String proxyHost = Preferences.getPreference(PreferenceUtils.chainProxyHttp, null);
            if (proxyHost != null && !proxyHost.equals("") && proxyCredentialsUsername != null && !proxyCredentialsUsername.equals("")){
             String[] userNameParts =  proxyCredentialsUsername.split("\\\\");
             String[] hostParts = proxyHost.split(":");
              _credentialManager.addDomainCredentials(new DomainCredential(hostParts[0], userNameParts[0], userNameParts[1], proxyCredentialsPassword));
              _credentialManager.addBasicCredentials(new BasicCredential(hostParts[0], userNameParts[0], userNameParts[1], proxyCredentialsPassword));
              _credentialManager.addDigestCredentials(new DigestCredential(hostParts[0], userNameParts[0], userNameParts[1], proxyCredentialsPassword));
            }
        }
        
        _captureData = Preferences.getPreferenceBoolean(PreferenceUtils.proxyCaptureData, false);

        configureHTTPClient(mContext);
        
        String dropRegex = Preferences.getPreference(PreferenceUtils.dataCaptureBlackListRegEx, null);
        try {
            setDropPattern(dropRegex);
        } catch (PatternSyntaxException pse) {
            _logger.warning("Got an invalid regular expression for conversations to ignore: " + dropRegex + " results in " + pse.toString());
        }
        String whitelistRegex = Preferences.getPreference(PreferenceUtils.dataCaptureWhiteListRegEx, null);
        try {
            setWhitelistPattern(whitelistRegex);
        } catch (PatternSyntaxException pse) {
            _logger.warning("Got an invalid regular expression for conversations to whitelist: " + whitelistRegex + " results in " + pse.toString());
        }
         this.analysisQueuedExecutor = new QueuedExecutor();
        this.analysisQueuedExecutor.setThreadFactory(new QueueProcessorThreadFactory("QueueProcessor"));
        this.analysisLongRunningQueuedExecutor = new QueuedExecutor();
        this.analysisLongRunningQueuedExecutor.setThreadFactory(new QueueProcessorThreadFactory("Long Running QueueProcessor"));
    }
    
    private static final class QueueProcessorThreadFactory implements ThreadFactory {

        private final String threadName;
        
        public QueueProcessorThreadFactory(String threadName) {
            this.threadName = threadName;
        }
        
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r);
            thread.setName(this.threadName);
            thread.setDaemon(true);
            thread.setPriority(Thread.MIN_PRIORITY);
            return thread;
        }
    }
    
    public Context getAndroidContext(){
        return mContext;
    }
    
    public int getProxyPortFromSettings(){
        int defaultValue = 8008;
        try{
            String strPort = Preferences.getPreference("preference_proxy_port", "8008");
            return Integer.parseInt(strPort);
        }catch (Exception ex){
            _logger.warning("Error parsing port. Set to" + String.valueOf(defaultValue) + " :" + ex.getMessage());
            return defaultValue;
        }
        
    }
    
    public List<String> getListeners(){
        List<String> listeners = new ArrayList<String>();
        String strPort = Preferences.getPreference(PreferenceUtils.proxyPort, "8008");
        listeners.add("127.0.0.1:" + strPort);
        try{
            boolean listenNonLocal = Preferences.getPreferenceBoolean(PreferenceUtils.proxyListenNonLocal, false);
            if (listenNonLocal){
                List<String> nonLocalAdresses = NetworkUtils.getLocalIpAddress();
                for (Iterator<String> iterator = nonLocalAdresses.iterator(); iterator
                        .hasNext();) {
                    String adress = (String) iterator.next();
                    listeners.add(adress + ":" + strPort);
                }
            }
        }catch (Exception ex){
            _logger.warning("Error gettting listeners:" + ex.getMessage());
        }
        return listeners;
    }
    
    public ScriptManager getScriptManager() {
        // sandrop commented until used
        //return _scriptManager;
        return null;
    }
    
    public CredentialManager getCredentialManager() {
        return _credentialManager;
    }
    
    public String getDropPattern() {
        return dropPattern == null ? "" : dropPattern.pattern();
    }
    public void setWhitelistPattern(String pattern) throws PatternSyntaxException{
        if (pattern == null || "".equals(pattern)) {
            whitelistPattern = null;
        } else {
            whitelistPattern = Pattern.compile(pattern);
            _logger.finest("Using whitelist regular expression " + pattern);
        }
    }
    public void setDropPattern(String pattern) throws PatternSyntaxException {
        if (pattern == null || "".equals(pattern)) {
            dropPattern = null;
        } else {
            dropPattern = Pattern.compile(pattern);
            _logger.finest("Using blacklist regular expression " + pattern);
        }
    }
    
    /**
     * instructs the framework to use the provided model. The framework notifies all
     * plugins that the session has changed.
     */
    public void setSession(String type, Object store, String session) throws StoreException {
        _model.setSession(type, store, session);
        Iterator<Plugin> it = _plugins.iterator();
        while (it.hasNext()) {
            Plugin plugin = it.next();
            if (!plugin.isRunning()) {
                plugin.setSession(type, store, session);
            } else {
                _logger.warning(plugin.getPluginName() + " is running while we are setting the session");
            }
        }
    }
    
    /**
     * provided to allow plugins to gain access to the model.
     * @return the SiteModel
     */
    public FrameworkModel getModel() {
        return _model;
    }
    
    private void extractVersionFromManifest() {
        Package pkg = Package.getPackage("org.owasp.webscarab");
        if (pkg != null) _version = pkg.getImplementationVersion();
        else _logger.severe("PKG is null");
        if (_version == null) _version = "unknown (local build?)";
    }
    
    /**
     * adds a new plugin into the framework
     * @param plugin the plugin to add
     */
    public void addPlugin(Plugin plugin) {
        _plugins.add(plugin);
        Hook[] hooks = plugin.getScriptingHooks();
        
        // sandrop commented until fixed
        //_scriptManager.registerHooks(plugin.getPluginName(), hooks);
    }
    
    /** 
     * retrieves the named plugin, if it exists
     * @param name the name of the plugin
     * @return the plugin if it exists, or null
     */
    public Plugin getPlugin(String name) {
        Plugin plugin = null;
        Iterator<Plugin> it = _plugins.iterator();
        while (it.hasNext()) {
            plugin = it.next();
            if (plugin.getPluginName().equals(name)) return plugin;
        }
        return null;
    }
    
    /**
     * starts all the plugins in the framework
     */
    public void startPlugins() {
        HTTPClientFactory.getValidInstance().getSSLContextManager().invalidateSessions();
        Iterator<Plugin> it = _plugins.iterator();
        while (it.hasNext()) {
            Plugin plugin = it.next();
            if (!plugin.isRunning()) {
                Thread t = new Thread(plugin, plugin.getPluginName());
                t.setDaemon(true);
                t.start();
            } else {
                _logger.warning(plugin.getPluginName() + " was already running");
            }
        }
        // sandrop commented until used
        //_scriptManager.loadScripts();
    }
    
    public boolean isBusy() {
        Iterator<Plugin> it = _plugins.iterator();
        while (it.hasNext()) {
            Plugin plugin = it.next();
            if (plugin.isBusy()) return true;
        }
        return false;
    }
    
    public boolean isRunning() {
        Iterator<Plugin> it = _plugins.iterator();
        while (it.hasNext()) {
            Plugin plugin = it.next();
            if (plugin.isRunning()) return true;
        }
        return false;
    }
    
    public boolean isModified() {
        if (_model.isModified()) return true;
        Iterator<Plugin> it = _plugins.iterator();
        while (it.hasNext()) {
            Plugin plugin = it.next();
            if (plugin.isModified()) return true;
        }
        return false;
    }
    
    public String[] getStatus() {
        List<String> status = new ArrayList<String>();
        Iterator<Plugin> it = _plugins.iterator();
        while (it.hasNext()) {
            Plugin plugin = it.next();
            status.add(plugin.getPluginName() + " : " + plugin.getStatus());
        }
        return status.toArray(new String[0]);
    }
    
    public void start(){
        configureHTTPClient(mContext);
        startPlugins();
    }
    
    public void stop(){
        try {
            stopPlugins();
            saveSessionData();
            HTTPClientFactory.invalidateInstance();
        } catch (Exception e) {
            _logger.log(Level.SEVERE, "Error stoping " + e.getMessage());
        }
    }
    
    /**
     * stops all the plugins in the framework
     * @throws InterruptedException 
     */
    public boolean stopPlugins() throws InterruptedException {
        // sandrob force stop not looking at if (isBusy()) return false;
        Iterator<Plugin> it = _plugins.iterator();
        while (it.hasNext()) {
            Plugin plugin = it.next();
            if (plugin.isRunning()) {
                // _logger.info("Stopping " + plugin.getPluginName());
                plugin.stop();
                // _logger.info("Done");
            } else {
                _logger.warning(plugin.getPluginName() + " was not running");
            }
        }
        // sandrop commented until used
        //_scriptManager.saveScripts();
        return true;
    }
    
    /**
     * called to instruct the various plugins to save their current state to the store.
     * @throws StoreException if there is any problem saving the session data
     */
    public void saveSessionData() throws StoreException {
        StoreException storeException = null;
        if (_model.isModified()) {
            _logger.info("Flushing model");
            _model.flush();
            _logger.info("Done");
        }
        Iterator<Plugin> it = _plugins.iterator();
        while (it.hasNext()) {
            Plugin plugin = it.next();
            if (plugin.isModified()) {
                try {
                    _logger.info("Flushing " + plugin.getPluginName());
                    plugin.flush();
                    _logger.info("Done");
                } catch (StoreException se) {
                    if (storeException == null) storeException = se;
                    _logger.severe("Error saving data for " + plugin.getPluginName() + ": " + se);
                }
            }
        }
        
        if (storeException != null) throw storeException;
    }
    
    /**
     * returns the build version of WebScarab. This is extracted from the webscarab.jar
     * Manifest, if webscarab is running from a jar.
     * @return the version string
     */
    public String getVersion() {
        return _version;
    }
    
    public ConversationID reserveConversationID() {
        return _model.reserveConversationID();
    }
    
    private boolean canStore(Request request){
        //Do we have whitelisting? If so, check if it matches
        if (request == null) {
            return false;
        }
        if (!_captureData){
            return false;
        }
        if(whitelistPattern != null && !whitelistPattern.matcher(request.getURL().toString()).matches())
        {
            _logger.finest("storage skipped. Url not in whitelist");
            return false;
        }
        // Also, check blacklist - drop pattern
        
        if (dropPattern != null && dropPattern.matcher(request.getURL().toString()).matches()) {
            _logger.finest("storage skipped. Url is in blacklist");
            return false;
        }
        return true;
    }
    
    public long createConversation(Request request, Date when, int type, ConnectionDescriptor connectionDescriptor) {
        if (!canStore(request)) return -1;
        return _model.createNewConversation(when, type, connectionDescriptor);
    }
    
    public long gotRequest(long conversationId, Date when, Request request){
        if (!canStore(request)) return -1;
        // to be sure that all data is read from request before making store
        request.flushContentStream();
        return _model.updateGotRequestConversation(conversationId, when, request);
    }
    
    public long gotResponse(long conversationId, Date when, Request request, Response response, boolean dataModified){
        if (!canStore(request)) return -1;
        Request requestToStore = null;
        if (dataModified){
            requestToStore = request;
        }
        return _model.updateGotResponseConversation(conversationId, when, requestToStore, response);
    }
    
    public long failedResponse(long conversationId, Date when, Request request, Response response, String reason, boolean dataModified){
        if (!canStore(request)) return -1;
        Request requestToStore = null;
        if (dataModified){
            requestToStore = request;
        }
        return _model.updateFailedConversation(conversationId, when, requestToStore, reason);
    }
    
    /*
    public long updateConversation(long conversationId, Date when, Request request, Response response) {
        if (!canStore(request)) return -1;
        return _model.updateConversation(conversationId, when, request, response);
    }
    */
    
    
    public void cleanConversation(Request request, Response response){
        // clean is needed because there can be some temp files for request/response big content
        if (request != null){
            request.clean();
        }
        if (response != null){
           response.clean();
        }
    }
    
    public void addConversation(ConversationID id, Request request, Response response, String origin) {
        addConversation(id, new Date(), request, response, origin);
    }
    
    public void addConversation(ConversationID id, Date when, Request request, Response response, String origin) {
        ScriptableConversation conversation = new ScriptableConversation(id, request, response, origin);
        _allowAddConversation.runScripts(conversation);
        if (conversation.isCancelled()) return;
        //Do we have whitelisting? If so, check if it matches
        if(whitelistPattern != null && !whitelistPattern.matcher(request.getURL().toString()).matches())
        {
        	_logger.finest("storage skipped. Url not in whitelist");
            return;
        }
        // Also, check blacklist - drop pattern
        
        if (dropPattern != null && dropPattern.matcher(request.getURL().toString()).matches()) {
            _logger.finest("storage skipped. Url is in blacklist");
            return;
        }
        _model.addConversation(id, when, request, response, origin);
        if (!conversation.shouldAnalyse()) return;
        _analyseConversation.runScripts(conversation);
        try {
            if (_model.haveValidStore()){
                // sandrop not used yet and having problems with script interceptor
                // this.analysisQueuedExecutor.execute(new QueueProcessor(id));
                // this.analysisLongRunningQueuedExecutor.execute(new QueueProcessor(id, true));
            }
        } catch (Exception ex) {
            _logger.severe("error scheduling analysis task: " + ex.getMessage());
        }
    }
    
    public ConversationID addConversation(Request request, Response response, String origin) {
        ConversationID id = reserveConversationID();
        addConversation(id, new Date(), request, response, origin);
        return id;
    }
    
    private void configureHTTPClient(Context context) {
        HTTPClientFactory factory = HTTPClientFactory.getInstance(context);
        String prop = null;
        String value;
        int colon;
        try {
            // FIXME for some reason, we get "" instead of null for value,
            // and do not use our default value???
            value = null;
            if (Preferences.getPreferenceBoolean(PreferenceUtils.chainProxyEnabled, false)){
                value = Preferences.getPreference(PreferenceUtils.chainProxyHttp);
            }

            if (value == null || value.equals("")) value = ":3128";
            colon = value.indexOf(":");
            factory.setHttpProxy(value.substring(0,colon), Integer.parseInt(value.substring(colon+1).trim()));

            String domainName = Preferences.getPreference(PreferenceUtils.localDomainName, null);
            factory.setLocalDomainName(domainName);

            if (value == null || value.equals("")) value = ":3128";
            colon = value.indexOf(":");
            factory.setHttpProxy(value.substring(0,colon), Integer.parseInt(value.substring(colon+1).trim()));
            
            
            
            value = null;
            if (Preferences.getPreferenceBoolean(PreferenceUtils.chainProxyEnabled, false)){
                value = Preferences.getPreference(PreferenceUtils.chainProxyHttps);
            }
            if (value == null || value.equals("")) value = ":3128";
            colon = value.indexOf(":");
            factory.setHttpsProxy(value.substring(0,colon), Integer.parseInt(value.substring(colon+1).trim()));

            value = null;
            if (Preferences.getPreferenceBoolean(PreferenceUtils.chainProxyEnabled, false)){
                value = Preferences.getPreference(PreferenceUtils.chainProxyExcludeList);
            }
            if (value == null) value = "";
            factory.setNoProxy(value.split(" *, *"));
            
            int connectTimeout = 30000;
            prop = "HttpClient.connectTimeout";
            value = Preferences.getPreference(prop,"");
            if (value != null && !value.equals("")) {
                try {
                    connectTimeout = Integer.parseInt(value);
                } catch (NumberFormatException nfe) {}
            }
            int readTimeout = 0;
            prop = "HttpClient.readTimeout";
            value = Preferences.getPreference(prop,"");
            if (value != null && !value.equals("")) {
                try {
                    readTimeout = Integer.parseInt(value);
                } catch (NumberFormatException nfe) {}
            }
            factory.setTimeouts(connectTimeout, readTimeout);
            
        } catch (NumberFormatException nfe) {
            _logger.warning("Error parsing property " + prop + ": " + nfe);
        } catch (Exception e) {
            _logger.warning("Error configuring the HTTPClient property " + prop + ": " + e);
        }
        factory.setAuthenticator(_credentialManager);
    }
    
    private class QueueProcessor implements Runnable {
        
        private final ConversationID id;
        
        private final boolean longRunning;
        
        public QueueProcessor(ConversationID id) {
            this(id, false);
        }
        
        public QueueProcessor(ConversationID id, boolean longRunning) {
            this.id = id;
            this.longRunning = longRunning;
        }
        
        public void run() {
            if (null == this.id) {
                return;
                }
                    Request request = _model.getRequest(id);
                    Response response = _model.getResponse(id);
                    String origin = _model.getConversationOrigin(id);
            Iterator<Plugin> it = _plugins.iterator();
                    while (it.hasNext()) {
                Plugin plugin = it.next();
                if (this.longRunning) {
                    if (false == plugin instanceof Fragments) {
                        continue;
                    }
                    _logger.info("running long running analysis: " + plugin.getPluginName());
                } else {
                    if (plugin instanceof Fragments) {
                        continue;
                    }
                }
                        if (plugin.isRunning()) {
                            try {
                        long t0 = System.currentTimeMillis();
                                plugin.analyse(id, request, response, origin);
                        long t1 = System.currentTimeMillis();
                        long dt = t1 - t0;
                        if (dt > 1000 * 10) {
                            _logger.warning("plugin " + plugin.getPluginName() + " is taking a long time to analyse conversation " + id + " (" + dt + " milliseconds)");
                        }
                            } catch (Exception e) {
                                _logger.warning(plugin.getPluginName() + " failed to process " + id + ": " + e);
                                e.printStackTrace();
                            }
                        }
                    }
                }
    }
    
    private class AddConversationHook extends Hook {
        
        public AddConversationHook() {
            super("Add Conversation", 
            "Called when a new conversation is added to the framework.\n" +
            "Use conversation.setCancelled(boolean) and conversation.setAnalyse(boolean) " +
            "after deciding using conversation.getRequest() and conversation.getResponse()");
        }
        
        public void runScripts(ScriptableConversation conversation) {
            if (_bsfManager == null) return;
            synchronized(_bsfManager) {
                try {
                    _bsfManager.declareBean("conversation", conversation, conversation.getClass());
                    super.runScripts();
                    _bsfManager.undeclareBean("conversation");
                } catch (Exception e) {
                    _logger.severe("Declaring or undeclaring a bean should not throw an exception! " + e);
                }
            }
        }
        
    }

    private class AnalyseConversationHook extends Hook {
    	
        public AnalyseConversationHook() {
            super("Analyse Conversation", 
            "Called when a new conversation is added to the framework.\n" +
            "Use model.setConversationProperty(id, property, value) to assign properties");
        }
        
        public void runScripts(ScriptableConversation conversation) {
            if (_bsfManager == null) return;
            synchronized(_bsfManager) {
                try {
                    _bsfManager.declareBean("id", conversation.getId(), conversation.getId().getClass());
                    _bsfManager.declareBean("conversation", conversation, conversation.getClass());
                    _bsfManager.declareBean("model", _wrapper, _wrapper.getClass());
                    super.runScripts();
                    _bsfManager.undeclareBean("conversation");
                } catch (Exception e) {
                    _logger.severe("Declaring or undeclaring a bean should not throw an exception! " + e);
                }
            }
        }
    }
}
