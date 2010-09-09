/* File:        $Id$
 * Revision:    $Revision$
 * Author:      $Author$
 * Date:        $Date$
 *
 * The Netarchive Suite - Software to harvest and preserve websites
 * Copyright 2004-2009 Det Kongelige Bibliotek and Statsbiblioteket, Denmark
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */

package dk.netarkivet.harvester.harvesting;

import java.io.File;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.dom4j.Document;

import dk.netarkivet.common.CommonSettings;
import dk.netarkivet.common.Constants;
import dk.netarkivet.common.exceptions.ArgumentNotValid;
import dk.netarkivet.common.exceptions.IOFailure;
import dk.netarkivet.common.utils.FileUtils;
import dk.netarkivet.common.utils.Settings;
import dk.netarkivet.common.utils.XmlUtils;

/**
 * This class encapsulates all the files that Heritrix gets from our system,
 * and all files we read from Heritrix.
 */
public class HeritrixFiles {
    /** The directory that crawls are performed in. */
    private final File crawlDir;
    /** The prefix we put on generated ARC files. */
    private final String arcFilePrefix;

    /** The job ID this object represents files for. */
    private final Long jobID;
    /** The job ID this object represents files for. */
    private final Long harvestID;

    /** The JMX password file to be used by Heritrix. */
    private final File jmxPasswordFile;
    /** The JMX access file to be used by Heritrix. */
    private final File jmxAccessFile;

    /** The name of the order.xml file. */
    private static final String ORDER_XML_FILENAME = "order.xml";

    /** The name of the seeds.txt file. */
    private static final String SEEDS_TXT_FILENAME = "seeds.txt";

    /** The name of the index directory. */
    private File indexDir;

    /** The logger. */
    private Log log = LogFactory.getLog(getClass().getName());

    /** The name of the progress statistics log. */
    private static final String PROGRESS_STATISTICS_LOG_FILENAME
            = "progress-statistics.log";
    /** The name of the crawl log. */
    private static final String CRAWL_LOG_FILENAME = "crawl.log";
    /** The name of the stdout/stderr file from Heritrix. */
    private static final String OUTPUT_FILENAME = "heritrix.out";

    /** Create a new HeritrixFiles object for a job.
     *
     * @param crawlDir The dir, where the crawl-files are placed.
     * Assumes, that crawlDir exists already.
     * @param jobID The JobID of this crawl.
     * @param harvestID The harvestID of this crawl.
     * @param jmxPasswordFile The jmx password file to be used by Heritrix.
     * The existence of this file is checked another place.
     * @param jmxAccessFile The JMX access file to be used by Heritrix.
     * The existence of this file is checked another place.
     * @throws ArgumentNotValid if null crawlDir,
     *  or non-positive jobID and harvestID.
     */
    public HeritrixFiles(File crawlDir, long jobID, long harvestID,
            File jmxPasswordFile, File jmxAccessFile) {
        ArgumentNotValid.checkNotNull(crawlDir, "crawlDir");
        ArgumentNotValid.checkPositive(jobID, "jobID");
        ArgumentNotValid.checkPositive(harvestID, "harvestID");
        ArgumentNotValid.checkNotNull(jmxPasswordFile, "jmxPasswordFile");
        ArgumentNotValid.checkNotNull(jmxAccessFile, "jmxAccessFile");
        this.crawlDir = crawlDir;
        this.arcFilePrefix = jobID + "-" + harvestID;
        this.jobID = jobID;
        this.harvestID = harvestID;
        this.jmxPasswordFile = jmxPasswordFile;
        this.jmxAccessFile = jmxAccessFile;
    }

    /**
     * Alternate constructor that by default reads the jmxPasswordFile,
       and jmxAccessFile from the current settings.
     * @param crawlDir The dir, where the crawl-files are placed
     * @param jobID The JobID of this crawl.
     * @param harvestID The harvestID of this crawl.
     */
     public HeritrixFiles(File crawlDir, long jobID, long harvestID) {
         this(crawlDir, jobID, harvestID,
                 new File(Settings.get(CommonSettings.JMX_PASSWORD_FILE)),
                 new File(Settings.get(CommonSettings.JMX_ACCESS_FILE)));
     }
    
    /** Returns the directory that crawls are performed inside.
     *
     * @return A directory (that is created as part of harvest setup) that
     * all of Heritrix' files live in.
     */
    public File getCrawlDir() {
        return crawlDir;
    }

    /** Returns the prefix used to generate ARC files.
     *
     * @return The ARC file prefix, currently jobID-harvestID.
     */
    public String getArcFilePrefix() {
        return arcFilePrefix;
    }

    /** Returns the order.xml file object.
     *
     * @return A file object for the order.xml file (which may not have been
     * written yet).
     */
    public File getOrderXmlFile() {
        return new File(crawlDir, ORDER_XML_FILENAME);
    }

    /** Returns the seeds.txt file object.
     *
     * @return A file object for the seeds.txt file (which may not have been
     * written yet).
     */
    public File getSeedsTxtFile() {
        return new File(crawlDir, SEEDS_TXT_FILENAME);
    }

    /**
     * Writes the given content to the seeds.txt file.
     *
     * @param seeds The intended content of seeds.txt
     * @throws ArgumentNotValid if seeds is null or empty
     */
    public void writeSeedsTxt(String seeds) {
        ArgumentNotValid.checkNotNullOrEmpty(seeds, "String seeds");
        log.debug("Writing seeds to disk as file: " 
                + getSeedsTxtFile().getAbsolutePath());
        FileUtils.writeBinaryFile(getSeedsTxtFile(), seeds.getBytes());
    }

    /**
     * Writes the given order.xml content to the order.xml file.
     *
     * @param doc The intended content of order.xml
     * @throws ArgumentNotValid, if doc is null or empty
     */
    public void writeOrderXml(Document doc) {
        ArgumentNotValid.checkNotNull(doc, "Document doc");
        ArgumentNotValid.checkTrue(doc.hasContent(),
                "XML document must not be empty");
        log.debug("Writing order-file to disk as file: "
                + getOrderXmlFile().getAbsolutePath());
        XmlUtils.writeXmlToFile(doc, getOrderXmlFile());
    }

    /** Get the file that contains output from Heritrix on stdout/stderr.
     *
     * @return File that contains output from Heritrix on stdout/stderr.
     */
    public File getHeritrixOutput() {
        return new File(crawlDir, OUTPUT_FILENAME);
    }

    /**
     * Set the deduplicate index dir.
     * @param indexDir the cache dir containing unzipped files
     * @throws ArgumentNotValid if indexDir is not a directory or is null
     */
    public void setIndexDir(File indexDir) {
        ArgumentNotValid.checkNotNull(indexDir, "File indexDir");
        ArgumentNotValid.checkTrue(indexDir.isDirectory(),
                "indexDir '" + indexDir + "' should be a directory");
        this.indexDir = indexDir;
        log.debug("Setting deduplication index dir '" + indexDir + "'");
    }

    /**
     * Returns the index directory, if one has been set.
     * @return the index directory or null if no index has been set.
     */
    public File getIndexDir() {
        return indexDir;
    }

    /**
     * Return a list of disposable heritrix-files.
     * Currently the list consists of the File "state.job", and
     * the directories: "checkpoints", "state", "scratch".
     *
     * @return a list of disposable heritrix-files.
     */
    public File[] getDisposableFiles() {
        return new File[] {
                new File(crawlDir, "state.job"),
                new File(crawlDir, "state"),
                new File(crawlDir, "checkpoints"),
                new File(crawlDir, "scratch")
        };
    }

    /**
     * Retrieve the crawlLog as a File object.
     * @return the crawlLog as a File object.
     */
    public File getCrawlLog() {
        File logDir = new File(crawlDir, "logs");
        return new File(logDir, CRAWL_LOG_FILENAME);
    }

    /**
     * Retrieve the progress statistics log as a File object.
     * @return the progress statistics log as a File object.
     */
    public File getProgressStatisticsLog() {
        File logDir = new File(crawlDir, "logs");
        return new File(logDir, PROGRESS_STATISTICS_LOG_FILENAME);
    }

    /**
     * Get the job ID.
     * @return Job ID this heritrix files object is for.
     */
    public Long getJobID() {
        return jobID;
    }

    /**
     * Get the harvest ID.
     * @return Harvest ID this heritrix files object is for.
     */
    public Long getHarvestID() {
        return harvestID;
    }

    /**
     * Delete statefile etc. and move crawl directory to oldjobs.
     *
     * @param oldJobsDir Directory to move the rest of any existing files to.
     */
    public void cleanUpAfterHarvest(File oldJobsDir) {
        // delete disposable files
        for (File disposable : getDisposableFiles()) {
            if (disposable.exists()) {
                try {
                    FileUtils.removeRecursively(disposable);
                } catch (IOFailure e) {
                    //Log harmless trouble
                    log.debug("Couldn't delete leftover file '"
                               + disposable.getAbsolutePath() + "'", e);
                }
            }
        }
        // move the rest to oldjobs
        FileUtils.createDir(oldJobsDir);
        File destDir = new File(oldJobsDir, crawlDir.getName());
        boolean success = crawlDir.renameTo(destDir);
        if (!success) {
            String message = "Failed to rename jobdir '"
                             + crawlDir + "' to '" + destDir + "'";
            log.warn(message);
        }
    }

    /**
     * Helper method to delete the crawl.log and progress statistics log.
     * Will log errors but otherwise continue.
     */
    public void deleteFinalLogs() {
        try {
            FileUtils.remove(getCrawlLog());
        } catch (IOFailure e) {
            //Log harmless trouble
            log.debug("Couldn't delete crawl log file.", e);
        }
        try {
            FileUtils.remove(getProgressStatisticsLog());
        } catch (IOFailure e) {
            //Log harmless trouble
            log.debug("Couldn't delete progress statistics log file.", e);
        }
    }
    
    /**
     * Return the directory, where Heritrix writes its arcfiles.
     * @return the directory, where Heritrix writes its arcfiles.
     */
    public File getArcsDir() {
        return new File(crawlDir, Constants.ARCDIRECTORY_NAME);
    }
    
    /**
     * Method for retrieving the jmxremote.password file.
     * 
     * @return the jmxPasswordFile.
     */
    public File getJmxPasswordFile() {
        return jmxPasswordFile;
    }

    /**
     * Method for retrieving the jmxremote.access file.
     * 
     * @return the jmxAccessFile.
     */
    public File getJmxAccessFile() {
        return jmxAccessFile;
    }
}