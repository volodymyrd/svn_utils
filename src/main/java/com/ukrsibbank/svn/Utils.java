package com.ukrsibbank.svn;

import java.io.File;
import java.util.Collections;

import org.tmatesoft.svn.core.SVNCancelException;
import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNLock;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.io.fs.FSRepositoryFactory;
import org.tmatesoft.svn.core.wc.ISVNEventHandler;
import org.tmatesoft.svn.core.wc.ISVNInfoHandler;
import org.tmatesoft.svn.core.wc.ISVNOptions;
import org.tmatesoft.svn.core.wc.ISVNStatusHandler;
import org.tmatesoft.svn.core.wc.SVNClientManager;
import org.tmatesoft.svn.core.wc.SVNCopySource;
import org.tmatesoft.svn.core.wc.SVNDiffClient;
import org.tmatesoft.svn.core.wc.SVNEvent;
import org.tmatesoft.svn.core.wc.SVNEventAction;
import org.tmatesoft.svn.core.wc.SVNInfo;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNRevisionRange;
import org.tmatesoft.svn.core.wc.SVNStatus;
import org.tmatesoft.svn.core.wc.SVNStatusType;
import org.tmatesoft.svn.core.wc.SVNUpdateClient;
import org.tmatesoft.svn.core.wc.SVNWCUtil;

public class Utils {

	private static SVNClientManager ourClientManager;
	private static ISVNEventHandler myCommitEventHandler;
	private static ISVNEventHandler myUpdateEventHandler;
	private static ISVNEventHandler myWCEventHandler;

	public Utils(String user, String password) throws SVNException {

		FSRepositoryFactory.setup();

		/*
		 * Creates a default run-time configuration options driver. Default
		 * options created in this way use the Subversion run-time configuration
		 * area (for instance, on a Windows platform it can be found in the
		 * '%APPDATA%\Subversion' directory).
		 * 
		 * readonly = true - not to save any configuration changes that can be
		 * done during the program run to a config file (config settings will
		 * only be read to initialize; to enable changes the readonly flag
		 * should be set to false).
		 * 
		 * SVNWCUtil is a utility class that creates a default options driver.
		 */
		ISVNOptions options = SVNWCUtil.createDefaultOptions(true);

		ISVNAuthenticationManager authManager = SVNWCUtil
				.createDefaultAuthenticationManager();

		/*
		 * Creates an instance of SVNClientManager providing a default auth
		 * manager and an options driver
		 */
		ourClientManager = SVNClientManager.newInstance(options, authManager);

		/*
		 * Creating custom handlers that will process events
		 */
		myCommitEventHandler = new CommitEventHandler();

		myUpdateEventHandler = new UpdateEventHandler();

		myWCEventHandler = new WCEventHandler();

		/*
		 * Registers a commit event handler
		 */
		ourClientManager.getCommitClient()
				.setEventHandler(myCommitEventHandler);

		/*
		 * Registers an update event handler
		 */
		ourClientManager.getUpdateClient()
				.setEventHandler(myUpdateEventHandler);

		/*
		 * Registers a WC event handler
		 */
		ourClientManager.getWCClient().setEventHandler(myWCEventHandler);

	}

	public void checkout(String repoUrl, File wcDir) throws SVNException {

		checkout(SVNURL.parseURIEncoded(repoUrl), SVNRevision.HEAD, wcDir, true);
	}

	public void update(File wcDir) throws SVNException {

		update(wcDir, SVNRevision.HEAD, true);
	}

	public void commit(File file, String message) throws SVNException {
		commit(file, false, message);
	}

	public void makeWorkingDir(String repoUrl, String rootPath, String version,
			String message) throws SVNException {

		File wd = new File(rootPath, "v_" + version);

		if (!wd.exists())
			wd.mkdir();

		SVNURL url = SVNURL.parseURIEncoded(repoUrl);
		
		makeDirectory(url, message);
		
		//importDirectory(wd, url, message, true);
		checkout(url, SVNRevision.HEAD, wd, true);
	}

	public void merge(String target, String rootPath, String version)
			throws SVNException {

		SVNDiffClient diffClient = ourClientManager.getDiffClient();
		SVNRevisionRange rangeToMerge = new SVNRevisionRange(
				SVNRevision.create(1), SVNRevision.HEAD);

		diffClient.doMerge(SVNURL.parseURIEncoded(target), SVNRevision.HEAD,
				Collections.singleton(rangeToMerge), new File(rootPath, "v_" + version),
				SVNDepth.INFINITY, true, false, false, false);
	}

	/**
	 * creates a new directory immediately in a repository
	 * 
	 * @param url
	 * @param commitMessage
	 * @return
	 * @throws SVNException
	 */
	private static SVNCommitInfo makeDirectory(SVNURL url, String commitMessage)
			throws SVNException {

		return ourClientManager.getCommitClient().doMkDir(new SVNURL[] { url },
				commitMessage);
	}

	/**
	 * imports a local directory to a repository
	 * 
	 * @param localPath
	 * @param dstURL
	 * @param commitMessage
	 * @param isRecursive
	 * @return
	 * @throws SVNException
	 */
	private static SVNCommitInfo importDirectory(File localPath, SVNURL dstURL,
			String commitMessage, boolean isRecursive) throws SVNException {

		return ourClientManager.getCommitClient().doImport(localPath, dstURL,
				commitMessage, isRecursive);
	}

	/**
	 * recursively commits Working Copy modifications to a repository
	 * 
	 * @param wcPath
	 * @param keepLocks
	 * @param commitMessage
	 * @return
	 * @throws SVNException
	 */
	private static SVNCommitInfo commit(File wcPath, boolean keepLocks,
			String commitMessage) throws SVNException {

		return ourClientManager.getCommitClient().doCommit(
				new File[] { wcPath }, keepLocks, commitMessage, null, null,
				false, false, SVNDepth.getInfinityOrEmptyDepth(true));
	}

	/**
	 * checks out a Working Copy given a repository url
	 * 
	 * @param url
	 * @param revision
	 * @param destPath
	 * @param isRecursive
	 * @return
	 * @throws SVNException
	 */
	private static long checkout(SVNURL url, SVNRevision revision,
			File destPath, boolean isRecursive) throws SVNException {

		SVNUpdateClient updateClient = ourClientManager.getUpdateClient();
		/*
		 * sets externals not to be ignored during the checkout
		 */
		updateClient.setIgnoreExternals(false);
		/*
		 * returns the number of the revision at which the working copy is
		 */

		return updateClient.doCheckout(url, destPath, revision, revision,
				SVNDepth.fromRecurse(isRecursive), false);
	}

	/**
	 * updates a Working Copy to a particular revision
	 * 
	 * @param wcPath
	 * @param updateToRevision
	 * @param isRecursive
	 * @return
	 * @throws SVNException
	 */
	private static long update(File wcPath, SVNRevision updateToRevision,
			boolean isRecursive) throws SVNException {

		SVNUpdateClient updateClient = ourClientManager.getUpdateClient();
		/*
		 * sets externals not to be ignored during the update
		 */
		updateClient.setIgnoreExternals(false);
		/*
		 * returns the number of the revision wcPath was updated to
		 */
		return updateClient.doUpdate(wcPath, updateToRevision,
				SVNDepth.fromRecurse(isRecursive), false, false);
	}

	/**
	 * switches a Working Copy to another url
	 * 
	 * @param wcPath
	 * @param url
	 * @param updateToRevision
	 * @param isRecursive
	 * @return
	 * @throws SVNException
	 */
	private static long switchToURL(File wcPath, SVNURL url,
			SVNRevision updateToRevision, boolean isRecursive)
			throws SVNException {

		SVNUpdateClient updateClient = ourClientManager.getUpdateClient();
		/*
		 * sets externals not to be ignored during the switch
		 */
		updateClient.setIgnoreExternals(false);
		/*
		 * returns the number of the revision wcPath was updated to
		 */
		return updateClient
				.doSwitch(wcPath, url, updateToRevision, isRecursive);
	}

	/**
	 * recursively adds an existing local item under version control (schedules
	 * for addition)
	 * 
	 * @param wcPath
	 * @throws SVNException
	 */
	private static void addEntry(File wcPath) throws SVNException {

		ourClientManager.getWCClient().doAdd(wcPath, false, false, false, true);
	}

	/**
	 * locks a versioned item
	 * 
	 * @param wcPath
	 * @param isStealLock
	 * @param lockComment
	 * @throws SVNException
	 */
	private static void lock(File wcPath, boolean isStealLock,
			String lockComment) throws SVNException {

		ourClientManager.getWCClient().doLock(new File[] { wcPath },
				isStealLock, lockComment);
	}

	/**
	 * deletes a versioned item from version control (schedules for deletion)
	 * 
	 * @param wcPath
	 * @param force
	 * @throws SVNException
	 */
	private static void delete(File wcPath, boolean force) throws SVNException {

		ourClientManager.getWCClient().doDelete(wcPath, force, false);
	}

	/**
	 * function copies or moves one location to another one within the same
	 * repository
	 * 
	 * @param srcURL
	 * @param dstURL
	 * @param isMove
	 * @param commitMessage
	 * @return
	 * @throws SVNException
	 */
	private static SVNCommitInfo copy(SVNURL srcURL, SVNURL dstURL,
			boolean isMove, boolean makeParents, boolean failWhenDstExists,
			String commitMessage, SVNProperties prop) throws SVNException {

		SVNCopySource[] src = new SVNCopySource[] { new SVNCopySource(
				SVNRevision.HEAD, SVNRevision.UNDEFINED, srcURL) };

		return ourClientManager.getCopyClient().doCopy(src, dstURL, isMove,
				makeParents, failWhenDstExists, commitMessage, prop);
	}

	/**
	 * status function
	 * 
	 * @param wcPath
	 * @param isRecursive
	 * @param isRemote
	 * @param isReportAll
	 * @param isIncludeIgnored
	 * @param isCollectParentExternals
	 * @throws SVNException
	 */
	private static void showStatus(File wcPath, boolean isRecursive,
			boolean isRemote, boolean isReportAll, boolean isIncludeIgnored,
			boolean isCollectParentExternals) throws SVNException {

		ourClientManager.getStatusClient().doStatus(wcPath, isRecursive,
				isRemote, isReportAll, isIncludeIgnored,
				isCollectParentExternals, new StatusHandler(isRemote));
	}

	/**
	 * info function
	 * 
	 * @param wcPath
	 * @param revision
	 * @param isRecursive
	 * @throws SVNException
	 */
	private static void showInfo(File wcPath, SVNRevision revision,
			boolean isRecursive) throws SVNException {
		ourClientManager.getWCClient().doInfo(wcPath, revision, isRecursive,
				new InfoHandler());
	}
}

/*
 * This class is an implementation of ISVNEventHandler intended for processing
 * events generated by do*() methods of an SVNCommitClient object. An instance
 * of this handler will be provided to an SVNCommitClient. When calling, for
 * example, SVNCommitClient.doCommit(..) on a WC path, this method will generate
 * an event for each 'adding'/'deleting'/'sending'/.. action it will perform
 * upon every path being committed. And this event is passed to
 * 
 * ISVNEventHandler.handleEvent(SVNEvent event, double progress)
 * 
 * to notify the handler. The event contains detailed information about the
 * path, action performed upon the path and some other.
 */
class CommitEventHandler implements ISVNEventHandler {
	/*
	 * progress is currently reserved for future purposes and now is always
	 * ISVNEventHandler.UNKNOWN
	 */
	public void handleEvent(SVNEvent event, double progress) {
		/*
		 * Gets the current action. An action is represented by SVNEventAction.
		 * In case of a commit an action can be determined via comparing
		 * SVNEvent.getAction() with SVNEventAction.COMMIT_-like constants.
		 */
		SVNEventAction action = event.getAction();
		if (action == SVNEventAction.COMMIT_MODIFIED) {
			System.out.println("Sending   " + event.getFile().getPath());
		} else if (action == SVNEventAction.COMMIT_DELETED) {
			System.out.println("Deleting   " + event.getFile().getPath());
		} else if (action == SVNEventAction.COMMIT_REPLACED) {
			System.out.println("Replacing   " + event.getFile().getPath());
		} else if (action == SVNEventAction.COMMIT_DELTA_SENT) {
			System.out.println("Transmitting file data....");
		} else if (action == SVNEventAction.COMMIT_ADDED) {
			/*
			 * Gets the MIME-type of the item.
			 */
			String mimeType = event.getMimeType();
			if (SVNProperty.isBinaryMimeType(mimeType)) {
				/*
				 * If the item is a binary file
				 */
				System.out.println("Adding  (bin)  "
						+ event.getFile().getPath());
			} else {
				System.out.println("Adding         "
						+ event.getFile().getPath());
			}
		}

	}

	/*
	 * Should be implemented to check if the current operation is cancelled. If
	 * it is, this method should throw an SVNCancelException.
	 */
	public void checkCancelled() throws SVNCancelException {
	}

}

/*
 * An implementation of ISVNInfoHandler that is used in WorkingCopy.java to
 * display info on a working copy path. This implementation is passed to
 * 
 * SVNWCClient.doInfo(File path, SVNRevision revision, boolean recursive,
 * ISVNInfoHandler handler)
 * 
 * For each item to be processed doInfo(..) collects information and creates an
 * SVNInfo which keeps that information. Then doInfo(..) calls implementor's
 * handler.handleInfo(SVNInfo) where it passes the gathered info.
 */
class InfoHandler implements ISVNInfoHandler {
	/*
	 * This is an implementation of ISVNInfoHandler.handleInfo(SVNInfo info).
	 * Just prints out information on a Working Copy path in the manner of the
	 * native SVN command line client.
	 */
	public void handleInfo(SVNInfo info) {
		System.out.println("-----------------INFO-----------------");
		System.out.println("Local Path: " + info.getFile().getPath());
		System.out.println("URL: " + info.getURL());
		if (info.isRemote() && info.getRepositoryRootURL() != null) {
			System.out.println("Repository Root URL: "
					+ info.getRepositoryRootURL());
		}
		if (info.getRepositoryUUID() != null) {
			System.out.println("Repository UUID: " + info.getRepositoryUUID());
		}
		System.out.println("Revision: " + info.getRevision().getNumber());
		System.out.println("Node Kind: " + info.getKind().toString());
		if (!info.isRemote()) {
			System.out.println("Schedule: "
					+ (info.getSchedule() != null ? info.getSchedule()
							: "normal"));
		}
		System.out.println("Last Changed Author: " + info.getAuthor());
		System.out.println("Last Changed Revision: "
				+ info.getCommittedRevision().getNumber());
		System.out.println("Last Changed Date: " + info.getCommittedDate());
		if (info.getPropTime() != null) {
			System.out
					.println("Properties Last Updated: " + info.getPropTime());
		}
		if (info.getKind() == SVNNodeKind.FILE && info.getChecksum() != null) {
			if (info.getTextTime() != null) {
				System.out.println("Text Last Updated: " + info.getTextTime());
			}
			System.out.println("Checksum: " + info.getChecksum());
		}
		if (info.getLock() != null) {
			if (info.getLock().getID() != null) {
				System.out.println("Lock Token: " + info.getLock().getID());
			}
			System.out.println("Lock Owner: " + info.getLock().getOwner());
			System.out.println("Lock Created: "
					+ info.getLock().getCreationDate());
			if (info.getLock().getExpirationDate() != null) {
				System.out.println("Lock Expires: "
						+ info.getLock().getExpirationDate());
			}
			if (info.getLock().getComment() != null) {
				System.out.println("Lock Comment: "
						+ info.getLock().getComment());
			}
		}
	}
}

/*
 * This is an implementation of ISVNStatusHandler & ISVNEventHandler that is
 * used in WorkingCopy.java to display status information. This implementation
 * is passed to
 * 
 * SVNStatusClient.doStatus(File path, boolean recursive, boolean remote,
 * boolean reportAll, boolean includeIgnored, boolean collectParentExternals,
 * ISVNStatusHandler handler)
 * 
 * For each item to be processed doStatus(..) collects status information and
 * creates an SVNStatus object which holds that information. Then doStatus(..)
 * calls an implementor's handler.handleStatus(SVNStatus) passing it the status
 * info collected.
 * 
 * StatusHandler will be also provided to an SVNStatusClient object as a handler
 * of events generated by a doStatus(..) method. For example, if the status is
 * invoked with the flag remote=true (like 'svn status -u' command), so then the
 * status operation will be finished with dispatching an SVNEvent to
 * ISVNEventHandler that will 'say' that the status is performed against the
 * youngest revision (the event holds that revision number).
 */
class StatusHandler implements ISVNStatusHandler, ISVNEventHandler {
	private boolean myIsRemote;

	public StatusHandler(boolean isRemote) {
		myIsRemote = isRemote;
	}

	public void handleStatus(SVNStatus status) {
		/*
		 * Gets the status of file/directory/symbolic link text contents. It is
		 * SVNStatusType who contains information on the state of an item.
		 */
		SVNStatusType contentsStatus = status.getContentsStatus();

		String pathChangeType = " ";

		boolean isAddedWithHistory = status.isCopied();
		if (contentsStatus == SVNStatusType.STATUS_MODIFIED) {
			/*
			 * The contents of the file have been Modified.
			 */
			pathChangeType = "M";
		} else if (contentsStatus == SVNStatusType.STATUS_CONFLICTED) {
			/*
			 * The item is in a state of Conflict.
			 */
			pathChangeType = "C";
		} else if (contentsStatus == SVNStatusType.STATUS_DELETED) {
			/*
			 * The item has been scheduled for Deletion from the repository.
			 */
			pathChangeType = "D";
		} else if (contentsStatus == SVNStatusType.STATUS_ADDED) {
			/*
			 * The item has been scheduled for Addition to the repository.
			 */
			pathChangeType = "A";
		} else if (contentsStatus == SVNStatusType.STATUS_UNVERSIONED) {
			/*
			 * The item is not under version control.
			 */
			pathChangeType = "?";
		} else if (contentsStatus == SVNStatusType.STATUS_EXTERNAL) {
			/*
			 * The item is unversioned, but is used by an eXternals definition.
			 */
			pathChangeType = "X";
		} else if (contentsStatus == SVNStatusType.STATUS_IGNORED) {
			/*
			 * The item is Ignored.
			 */
			pathChangeType = "I";
		} else if (contentsStatus == SVNStatusType.STATUS_MISSING
				|| contentsStatus == SVNStatusType.STATUS_INCOMPLETE) {
			/*
			 * The file, directory or symbolic link item is under version
			 * control but is missing or somehow incomplete.
			 */
			pathChangeType = "!";
		} else if (contentsStatus == SVNStatusType.STATUS_OBSTRUCTED) {
			/*
			 * The item is in the repository as one kind of object, but what's
			 * actually in the user's working copy is some other kind.
			 */
			pathChangeType = "~";
		} else if (contentsStatus == SVNStatusType.STATUS_REPLACED) {
			/*
			 * The item was Replaced in the user's working copy; that is, the
			 * item was deleted, and a new item with the same name was added
			 * (within a single revision).
			 */
			pathChangeType = "R";
		} else if (contentsStatus == SVNStatusType.STATUS_NONE
				|| contentsStatus == SVNStatusType.STATUS_NORMAL) {
			/*
			 * The item was not modified (normal).
			 */
			pathChangeType = " ";
		}

		/*
		 * If SVNStatusClient.doStatus(..) is invoked with remote = true the
		 * following code finds out whether the current item has been changed in
		 * the repository
		 */
		String remoteChangeType = " ";

		if (status.getRemotePropertiesStatus() != SVNStatusType.STATUS_NONE
				|| status.getRemoteContentsStatus() != SVNStatusType.STATUS_NONE) {
			/*
			 * the local item is out of date
			 */
			remoteChangeType = "*";
		}

		/*
		 * Now getting the status of properties of an item. SVNStatusType also
		 * contains information on the properties state.
		 */
		SVNStatusType propertiesStatus = status.getPropertiesStatus();

		/*
		 * Default - properties are normal (unmodified).
		 */
		String propertiesChangeType = " ";
		if (propertiesStatus == SVNStatusType.STATUS_MODIFIED) {
			/*
			 * Properties were modified.
			 */
			propertiesChangeType = "M";
		} else if (propertiesStatus == SVNStatusType.STATUS_CONFLICTED) {
			/*
			 * Properties are in conflict with the repository.
			 */
			propertiesChangeType = "C";
		}

		/*
		 * Whether the item was locked in the .svn working area (for example,
		 * during a commit or maybe the previous operation was interrupted, in
		 * this case the lock needs to be cleaned up).
		 */
		boolean isLocked = status.isLocked();
		/*
		 * Whether the item is switched to a different URL (branch).
		 */
		boolean isSwitched = status.isSwitched();
		/*
		 * If the item is a file it may be locked.
		 */
		SVNLock localLock = status.getLocalLock();
		/*
		 * If doStatus() was run with remote = true and the item is a file,
		 * checks whether a remote lock presents.
		 */
		SVNLock remoteLock = status.getRemoteLock();
		String lockLabel = " ";

		if (localLock != null) {
			/*
			 * at first suppose the file is locKed
			 */
			lockLabel = "K";
			if (remoteLock != null) {
				/*
				 * if the lock-token of the local lock differs from the lock-
				 * token of the remote lock - the lock was sTolen!
				 */
				if (!remoteLock.getID().equals(localLock.getID())) {
					lockLabel = "T";
				}
			} else {
				if (myIsRemote) {
					/*
					 * the local lock presents but there's no lock in the
					 * repository - the lock was Broken. This is true only if
					 * doStatus() was invoked with remote=true.
					 */
					lockLabel = "B";
				}
			}
		} else if (remoteLock != null) {
			/*
			 * the file is not locally locked but locked in the repository - the
			 * lock token is in some Other working copy.
			 */
			lockLabel = "O";
		}

		/*
		 * Obtains the working revision number of the item.
		 */
		long workingRevision = status.getRevision().getNumber();
		/*
		 * Obtains the number of the revision when the item was last changed.
		 */
		long lastChangedRevision = status.getCommittedRevision().getNumber();
		String offset = "                                ";
		String[] offsets = new String[3];
		offsets[0] = offset.substring(0, 6 - String.valueOf(workingRevision)
				.length());
		offsets[1] = offset.substring(0, 6 - String
				.valueOf(lastChangedRevision).length());
		offsets[2] = offset.substring(0,
				offset.length()
						- (status.getAuthor() != null ? status.getAuthor()
								.length() : 1));
		/*
		 * status is shown in the manner of the native Subversion command line
		 * client's command "svn status"
		 */
		System.out.println(pathChangeType
				+ propertiesChangeType
				+ (isLocked ? "L" : " ")
				+ (isAddedWithHistory ? "+" : " ")
				+ (isSwitched ? "S" : " ")
				+ lockLabel
				+ "  "
				+ remoteChangeType
				+ "  "
				+ workingRevision
				+ offsets[0]
				+ (lastChangedRevision >= 0 ? String
						.valueOf(lastChangedRevision) : "?") + offsets[1]
				+ (status.getAuthor() != null ? status.getAuthor() : "?")
				+ offsets[2] + status.getFile().getPath());
	}

	public void handleEvent(SVNEvent event, double progress) {
		SVNEventAction action = event.getAction();
		/*
		 * Print out the revision against which the status was performed. This
		 * event is dispatched when the SVNStatusClient.doStatus() was invoked
		 * with the flag remote set to true - that is for a local status it
		 * won't be dispatched.
		 */
		if (action == SVNEventAction.STATUS_COMPLETED) {
			System.out.println("Status against revision:  "
					+ event.getRevision());
		}

	}

	public void checkCancelled() throws SVNCancelException {
	}
}

/*
 * This class is an implementation of ISVNEventHandler intended for processing
 * events generated by do*() methods of an SVNUpdateClient object. An instance
 * of this handler will be provided to an SVNUpdateClient. When calling, for
 * example, SVNWCClient.doUpdate(..) on some path, that method will generate an
 * event for each 'update'/'add'/'delete'/.. action it will perform upon every
 * path being updated. And this event is passed to
 * 
 * ISVNEventHandler.handleEvent(SVNEvent event, double progress)
 * 
 * to notify the handler. The event contains detailed information about the
 * path, action performed upon the path and some other.
 */
class UpdateEventHandler implements ISVNEventHandler {
	/*
	 * progress is currently reserved for future purposes and now is always
	 * ISVNEventHandler.UNKNOWN
	 */
	public void handleEvent(SVNEvent event, double progress) {
		/*
		 * Gets the current action. An action is represented by SVNEventAction.
		 * In case of an update an action can be determined via comparing
		 * SVNEvent.getAction() and SVNEventAction.UPDATE_-like constants.
		 */
		SVNEventAction action = event.getAction();
		String pathChangeType = " ";
		if (action == SVNEventAction.UPDATE_ADD) {
			/*
			 * the item was added
			 */
			pathChangeType = "A";
		} else if (action == SVNEventAction.UPDATE_DELETE) {
			/*
			 * the item was deleted
			 */
			pathChangeType = "D";
		} else if (action == SVNEventAction.UPDATE_UPDATE) {
			/*
			 * Find out in details what state the item is (after having been
			 * updated).
			 * 
			 * Gets the status of file/directory item contents. It is
			 * SVNStatusType who contains information on the state of an item.
			 */
			SVNStatusType contentsStatus = event.getContentsStatus();
			if (contentsStatus == SVNStatusType.CHANGED) {
				/*
				 * the item was modified in the repository (got the changes from
				 * the repository
				 */
				pathChangeType = "U";
			} else if (contentsStatus == SVNStatusType.CONFLICTED) {
				/*
				 * The file item is in a state of Conflict. That is, changes
				 * received from the repository during an update, overlap with
				 * local changes the user has in his working copy.
				 */
				pathChangeType = "C";
			} else if (contentsStatus == SVNStatusType.MERGED) {
				/*
				 * The file item was merGed (those changes that came from the
				 * repository did not overlap local changes and were merged into
				 * the file).
				 */
				pathChangeType = "G";
			}
		} else if (action == SVNEventAction.UPDATE_EXTERNAL) {
			/* for externals definitions */
			System.out.println("Fetching external item into '"
					+ event.getFile().getAbsolutePath() + "'");
			System.out.println("External at revision " + event.getRevision());
			return;
		} else if (action == SVNEventAction.UPDATE_COMPLETED) {
			/*
			 * Updating the working copy is completed. Prints out the revision.
			 */
			System.out.println("At revision " + event.getRevision());
			return;
		} else if (action == SVNEventAction.ADD) {
			System.out.println("A     " + event.getFile().getPath());
			return;
		} else if (action == SVNEventAction.DELETE) {
			System.out.println("D     " + event.getFile().getPath());
			return;
		} else if (action == SVNEventAction.LOCKED) {
			System.out.println("L     " + event.getFile().getPath());
			return;
		} else if (action == SVNEventAction.LOCK_FAILED) {
			System.out
					.println("failed to lock    " + event.getFile().getPath());
			return;
		}

		/*
		 * Now getting the status of properties of an item. SVNStatusType also
		 * contains information on the properties state.
		 */
		SVNStatusType propertiesStatus = event.getPropertiesStatus();
		/*
		 * At first consider properties are normal (unchanged).
		 */
		String propertiesChangeType = " ";
		if (propertiesStatus == SVNStatusType.CHANGED) {
			/*
			 * Properties were updated.
			 */
			propertiesChangeType = "U";
		} else if (propertiesStatus == SVNStatusType.CONFLICTED) {
			/*
			 * Properties are in conflict with the repository.
			 */
			propertiesChangeType = "C";
		} else if (propertiesStatus == SVNStatusType.MERGED) {
			/*
			 * Properties that came from the repository were merged with the
			 * local ones.
			 */
			propertiesChangeType = "G";
		}

		/*
		 * Gets the status of the lock.
		 */
		String lockLabel = " ";
		SVNStatusType lockType = event.getLockStatus();

		if (lockType == SVNStatusType.LOCK_UNLOCKED) {
			/*
			 * The lock is broken by someone.
			 */
			lockLabel = "B";
		}

		System.out.println(pathChangeType + propertiesChangeType + lockLabel
				+ "       " + event.getFile().getPath());
	}

	/*
	 * Should be implemented to check if the current operation is cancelled. If
	 * it is, this method should throw an SVNCancelException.
	 */
	public void checkCancelled() throws SVNCancelException {
	}
}

/*
 * This class is an implementation of ISVNEventHandler intended for processing
 * events generated by do*() methods of an SVNWCClient object. An instance of
 * this handler will be provided to an SVNWCClient. When calling, for example,
 * SVNWCClient.doDelete(..) on some path, that method will generate an event for
 * each 'delete' action it will perform upon every path being deleted. And this
 * event is passed to
 * 
 * ISVNEventHandler.handleEvent(SVNEvent event, double progress)
 * 
 * to notify the handler. The event contains detailed information about the
 * path, action performed upon the path and some other.
 */
class WCEventHandler implements ISVNEventHandler {
	/*
	 * progress is currently reserved for future purposes and now is always
	 * ISVNEventHandler.UNKNOWN
	 */
	public void handleEvent(SVNEvent event, double progress) {
		/*
		 * Gets the current action. An action is represented by SVNEventAction.
		 */
		SVNEventAction action = event.getAction();
		if (action == SVNEventAction.ADD) {
			/*
			 * The item is scheduled for addition.
			 */
			System.out.println("A     " + event.getFile().getPath());
			return;
		} else if (action == SVNEventAction.COPY) {
			/*
			 * The item is scheduled for addition with history (copied, in other
			 * words).
			 */
			System.out.println("A  +  " + event.getFile().getPath());
			return;
		} else if (action == SVNEventAction.DELETE) {
			/*
			 * The item is scheduled for deletion.
			 */
			System.out.println("D     " + event.getFile().getPath());
			return;
		} else if (action == SVNEventAction.LOCKED) {
			/*
			 * The item is locked.
			 */
			System.out.println("L     " + event.getFile().getPath());
			return;
		} else if (action == SVNEventAction.LOCK_FAILED) {
			/*
			 * Locking operation failed.
			 */
			System.out
					.println("failed to lock    " + event.getFile().getPath());
			return;
		}
	}

	/*
	 * Should be implemented to check if the current operation is cancelled. If
	 * it is, this method should throw an SVNCancelException.
	 */
	public void checkCancelled() throws SVNCancelException {
	}
}