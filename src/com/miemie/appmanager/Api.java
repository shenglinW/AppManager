package com.miemie.appmanager;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Build;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.util.HashMap;
import java.util.List;
import java.util.StringTokenizer;

public final class Api {
	/** root script filename */
	private static final String SCRIPT_FILE = "Appmanager.sh";
	
	// Preferences
	public static final String PREFS_NAME         = "AppmanagerPrefs";
    public static final String PREF_DISABLE_UIDS   = "DisableUids";
    public static final String PREF_USED_UIDS   = "UsedUids";
	
    private static final int SDK_VERSION = Build.VERSION.SDK_INT;
    
	// Cached applications
	public static DroidApp applications[] = null;
	// Do we have root access?
	private static boolean hasroot = false;
	
    /**
     * Display a simple alert box
     * @param ctx context
     * @param msg message
     */
	public static void alert(Context ctx, CharSequence msg) {
    	if (ctx != null) {
        	new AlertDialog.Builder(ctx)
        	.setNeutralButton(android.R.string.ok, null)
        	.setMessage(msg)
        	.show();
    	}
    }
    
    public static void removeApps() {
        applications = null;
    }

    /**
     * @param ctx application context (mandatory)
     * @return a list of applications
     */
	public static DroidApp[] getApps(Context ctx) {
		if (applications != null) {
			// return cached instance
			return applications;
		}
        final SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, 0);
        // allowed application names separated by pipe '|' (persisted)
        final String savedUids_disable = prefs.getString(PREF_DISABLE_UIDS, "");
        int selected_disable[] = new int[0];
        
        if (savedUids_disable.length() > 0) {
            // Check which applications are allowed
            final StringTokenizer tok = new StringTokenizer(savedUids_disable, "|");
            selected_disable = new int[tok.countTokens()];
            for (int i = 0; i < selected_disable.length; i++) {
                final String uid = tok.nextToken();
                if (!uid.equals("")) {
                    try {
                        selected_disable[i] = Integer.parseInt(uid);
                    } catch (Exception ex) {
                        selected_disable[i] = -1;
                    }
                }
            }
        }
        
        final String savedUids_used = prefs.getString(PREF_USED_UIDS, "");
        int selected_used[] = new int[0];
        
        if (savedUids_used.length() > 0) {
            // Check which applications are allowed
            final StringTokenizer tok = new StringTokenizer(savedUids_used, "|");
            selected_used = new int[tok.countTokens()];
            for (int i = 0; i < selected_used.length; i++) {
                final String uid = tok.nextToken();
                if (!uid.equals("")) {
                    try {
                        selected_used[i] = Integer.parseInt(uid);
                    } catch (Exception ex) {
                        selected_used[i] = -1;
                    }
                }
            }
        }         
        
		try {
			final PackageManager pkgmanager = ctx.getPackageManager();
			final List<ApplicationInfo> installed = pkgmanager.getInstalledApplications(0);
			final HashMap<Integer, DroidApp> map = new HashMap<Integer, DroidApp>();
			final Editor edit = prefs.edit();
			boolean changed = false;
			String name = null;
			String cachekey = null;
			DroidApp app = null;
			for (final ApplicationInfo apinfo : installed) {
				boolean firstseem = false;
				app = map.get(apinfo.uid);
                /*
				if (app == null) {
					continue;
				}
				*/

				// try to get the application label from our cache - getApplicationLabel() is horribly slow!!!!
				cachekey = "cache.label."+apinfo.packageName;
				name = prefs.getString(cachekey, "");
				if (name.length() == 0) {
					// get label and put on cache
					name = pkgmanager.getApplicationLabel(apinfo).toString();
					edit.putString(cachekey, name);
					changed = true;
					firstseem = true;
				}
				if (app == null) {
					app = new DroidApp();
					app.uid = apinfo.uid;
					app.names = new String[] { name };
					app.appinfo = apinfo;
					map.put(apinfo.uid, app);
				} else {
					final String newnames[] = new String[app.names.length + 1];
					System.arraycopy(app.names, 0, newnames, 0, app.names.length);
					newnames[app.names.length] = name;
					app.names = newnames;
				}
				app.firstseem = firstseem;

                // check if this application is enabled
				app.enable = true;
                for (int index = 0; index < selected_disable.length; index++) {
                    if(selected_disable[index] == app.uid){
                        app.enable = false;
                        break;
                    }
                }

                // if (Arrays.binarySearch(selected_disable, app.uid) >= 0) {
                // app.enable = false;
                // } else {
                // app.enable = true;
                // }
                if (SDK_VERSION > 14) {
                    app.enable = apinfo.enabled;
                }

                for (int index = 0; index < selected_used.length; index++) {
                    if (selected_used[index] == app.uid) {
                        app.used = true;
                        break;
                    }
                }
			}
			if (changed) {
				edit.commit();
			}
			
			/* convert the map into an array */
			DroidApp temp[] = map.values().toArray(new DroidApp[map.size()]);
			applications = new DroidApp[map.size()];
			int count = 0;
            for (DroidApp app2 : temp) {
                if (!app2.enable) {
                    applications[count] = app2;
                    count++;
                }
            }
            for (DroidApp app3 : temp) {
                if (app3.enable && app3.used) {
                    applications[count] = app3;
                    count++;
                }
            }
            for (DroidApp app4 : temp) {
                if (app4.enable && !app4.used) {
                    applications[count] = app4;
                    count++;
                }
            }            
			//applications = map.values().toArray(new DroidApp[map.size()]);
			return applications;
		} catch (Exception e) {
			alert(ctx, "error: " + e);
		}
		return null;
	}
	/**
	 * Check if we have root access
	 * @param ctx mandatory context
     * @param showErrors indicates if errors should be alerted
	 * @return boolean true if we have root
	 */
	public static boolean hasRootAccess(final Context ctx, boolean showErrors) {
		if (hasroot) return true;
		final StringBuilder res = new StringBuilder();
		try {
			// Run an empty script just to check root access
			if (runScriptAsRoot(ctx, "exit 0", res) == 0) {
				hasroot = true;
				return true;
			}
		} catch (Exception e) {
		}
		if (showErrors) {
			alert(ctx, "Could not acquire root access.\n" +
				"You need a rooted phone to run DroidWall.\n\n" +
				"If this phone is already rooted, please make sure DroidWall has enough permissions to execute the \"su\" command.\n" +
				"Error message: " + res.toString());
		}
		return false;
	}
    /**
     * Runs a script, wither as root or as a regular user (multiple commands separated by "\n").
	 * @param ctx mandatory context
     * @param script the script to be executed
     * @param res the script output response (stdout + stderr)
     * @param timeout timeout in milliseconds (-1 for none)
     * @return the script exit code
     */
	public static int runScript(Context ctx, String script, StringBuilder res, long timeout, boolean asroot) {
		final File file = new File(ctx.getDir("bin",0), SCRIPT_FILE);
		final ScriptRunner runner = new ScriptRunner(file, script, res, asroot);
		runner.start();
		try {
			if (timeout > 0) {
				runner.join(timeout);
			} else {
				runner.join();
			}
			if (runner.isAlive()) {
				// Timed-out
				runner.interrupt();
				runner.join(150);
				runner.destroy();
				runner.join(50);
			}
		} catch (InterruptedException ex) {}
		return runner.exitcode;
	}
    /**
     * Runs a script as root (multiple commands separated by "\n").
	 * @param ctx mandatory context
     * @param script the script to be executed
     * @param res the script output response (stdout + stderr)
     * @param timeout timeout in milliseconds (-1 for none)
     * @return the script exit code
     */
	public static int runScriptAsRoot(Context ctx, String script, StringBuilder res, long timeout) {
		return runScript(ctx, script, res, timeout, true);
    }
    /**
     * Runs a script as root (multiple commands separated by "\n") with a default timeout of 20 seconds.
	 * @param ctx mandatory context
     * @param script the script to be executed
     * @param res the script output response (stdout + stderr)
     * @param timeout timeout in milliseconds (-1 for none)
     * @return the script exit code
     * @throws IOException on any error executing the script, or writing it to disk
     */
	public static int runScriptAsRoot(Context ctx, String script, StringBuilder res) throws IOException {
		return runScriptAsRoot(ctx, script, res, 40000);
	}
    /**
     * Runs a script as a regular user (multiple commands separated by "\n") with a default timeout of 20 seconds.
	 * @param ctx mandatory context
     * @param script the script to be executed
     * @param res the script output response (stdout + stderr)
     * @param timeout timeout in milliseconds (-1 for none)
     * @return the script exit code
     * @throws IOException on any error executing the script, or writing it to disk
     */
	public static int runScript(Context ctx, String script, StringBuilder res) throws IOException {
		return runScript(ctx, script, res, 40000, false);
	}
	
    /**
     * Save current rules using the preferences storage.
     * @param ctx application context (mandatory)
     */
    public static void saveRules(Context ctx) {
        final SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, 0);
        final DroidApp[] apps = getApps(ctx);
        // Builds a pipe-separated list of names
        final StringBuilder newuids_disable = new StringBuilder();
        final StringBuilder newuids_used = new StringBuilder();
        for (int i=0; i<apps.length; i++) {
            if (!apps[i].enable) {
                if (newuids_disable.length() != 0) newuids_disable.append('|');
                newuids_disable.append(apps[i].uid);
            }
            if (apps[i].used) {
                if (newuids_used.length() != 0) newuids_used.append('|');
                newuids_used.append(apps[i].uid);
            }            
        }
        // save the new list of UIDs
        final Editor edit = prefs.edit();
        edit.putString(PREF_DISABLE_UIDS, newuids_disable.toString());
        edit.putString(PREF_USED_UIDS, newuids_used.toString());
        edit.commit();
    }	
	
	/**
	 * enable/disable the app with given packagename
	 * @param ctx mandatory context
	 * @param enabled enabled flag
	 * @param packageName to enable/disable
	 */
    public static boolean setEnabled(Context ctx, boolean enabled, String packageName) {
        if (ctx == null)
            return false;
        if (hasRootAccess(ctx, false) == false) {
            return false;
        }
        final StringBuilder script = new StringBuilder();
        if (enabled) {
            script.append("pm enable " + packageName);
        } else {
            script.append("pm disable " + packageName);
        }
        
        final StringBuilder res = new StringBuilder();
        try {
            int code = runScriptAsRoot(ctx, script.toString(), res);
            if (code != 0) {
                String msg = res.toString();
                alert(ctx, "Error when exec cmd\n" + script.toString() + "\nExit code: " + code
                        + "\n\n" + msg.trim());
                return false;
            } else {
//                alert(ctx, "Successfully exec cmd\n" + script.toString());
                saveRules(ctx);
                return true;
            }
        } catch (IOException e) {
        }
        return false;
    }
	/**
	 * Called when an application in removed (un-installed) from the system.
	 * This will look for that application in the selected list and update the persisted values if necessary
	 * @param ctx mandatory app context
	 * @param uid UID of the application that has been removed
	 */
	public static void applicationRemoved(Context ctx, int uid) {

	}

    /**
     * Small structure to hold an application info
     */
    public static final class DroidApp {
        /** linux user id */
        int uid;
        /** application names belonging to this user id */
        String names[];
        
        boolean enable;
        boolean used;
        
        /** toString cache */
        String tostr;
        /** application info */
        ApplicationInfo appinfo;
        /** cached application icon */
        Drawable cached_icon;
        /** indicates if the icon has been loaded already */
        boolean icon_loaded;
        /** first time seem? */
        boolean firstseem;
        
        public DroidApp() {
        }
        public DroidApp(int uid, String name, boolean enable, boolean used) {
            this.uid = uid;
            this.names = new String[] {name};
            this.enable = enable;
            this.used = used;
        }
        /**
         * Screen representation of this application
         */
        @Override
        public String toString() {
            if (tostr == null) {
                final StringBuilder s = new StringBuilder();
//                if (uid > 0) s.append(uid + ": ");
                for (int i=0; i<names.length; i++) {
                    if (i != 0) s.append(", ");
                    s.append(names[i]);
                }
                s.append("\n");
                tostr = s.toString();
            }
            return tostr;
        }
    }    
	/**
	 * Internal thread used to execute scripts (as root or not).
	 */
	private static final class ScriptRunner extends Thread {
		private final File file;
		private final String script;
		private final StringBuilder res;
		private final boolean asroot;
		public int exitcode = -1;
		private Process exec;
		
		/**
		 * Creates a new script runner.
		 * @param file temporary script file
		 * @param script script to run
		 * @param res response output
		 * @param asroot if true, executes the script as root
		 */
		public ScriptRunner(File file, String script, StringBuilder res, boolean asroot) {
			this.file = file;
			this.script = script;
			this.res = res;
			this.asroot = asroot;
		}
		@Override
		public void run() {
			try {
				file.createNewFile();
				final String abspath = file.getAbsolutePath();
				// make sure we have execution permission on the script file
				Runtime.getRuntime().exec("chmod 777 "+abspath).waitFor();
				// Write the script to be executed
				final OutputStreamWriter out = new OutputStreamWriter(new FileOutputStream(file));
				if (new File("/system/bin/sh").exists()) {
					out.write("#!/system/bin/sh\n");
				}
				out.write(script);
				if (!script.endsWith("\n")) out.write("\n");
				out.write("exit\n");
				out.flush();
				out.close();
				if (this.asroot) {
					// Create the "su" request to run the script
					exec = Runtime.getRuntime().exec("su -c "+abspath);
				} else {
					// Create the "sh" request to run the script
					exec = Runtime.getRuntime().exec("sh "+abspath);
				}
				final InputStream stdout = exec.getInputStream();
				final InputStream stderr = exec.getErrorStream();
				final byte buf[] = new byte[8192];
				int read = 0;
				while (true) {
					final Process localexec = exec;
					if (localexec == null) break;
					try {
						// get the process exit code - will raise IllegalThreadStateException if still running
						this.exitcode = localexec.exitValue();
					} catch (IllegalThreadStateException ex) {
						// The process is still running
					}
					// Read stdout
					if (stdout.available() > 0) {
						read = stdout.read(buf);
						if (res != null) res.append(new String(buf, 0, read));
					}
					// Read stderr
					if (stderr.available() > 0) {
						read = stderr.read(buf);
						if (res != null) res.append(new String(buf, 0, read));
					}
					if (this.exitcode != -1) {
						// finished
						break;
					}
					// Sleep for the next round
					Thread.sleep(50);
				}
			} catch (InterruptedException ex) {
				if (res != null) res.append("\nOperation timed-out");
			} catch (Exception ex) {
				if (res != null) res.append("\n" + ex);
			} finally {
				destroy();
			}
		}
		/**
		 * Destroy this script runner
		 */
		public synchronized void destroy() {
			if (exec != null) exec.destroy();
			exec = null;
		}
	}
}
