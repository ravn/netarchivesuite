/*
 * #%L
 * Netarchivesuite - archive - test
 * %%
 * Copyright (C) 2005 - 2014 The Royal Danish Library, the Danish State and University Library,
 *             the National Library of France and the Austrian National Library.
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 2.1 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Lesser Public License for more details.
 * 
 * You should have received a copy of the GNU General Lesser Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/lgpl-2.1.html>.
 * #L%
 */
package dk.netarkivet.archive.bitarchive;

import java.io.File;
import java.io.OutputStream;

import dk.netarkivet.common.utils.batch.FileBatchJob;

/**
 * This class attempts to do illegal actions.
 */
@SuppressWarnings({"serial"})
public class EvilBatch2 extends FileBatchJob {
    public void initialize(OutputStream os) {
    }

    public boolean processFile(File file, OutputStream os) {
        try {
            file.delete();
        } catch (SecurityException e) {
            return false;
        }
        return true;
    }

    public void finish(OutputStream os) {
    }
}
