/*
 * #%L
 * Netarchivesuite - harvester
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

package dk.netarkivet.harvester.datamodel;

import java.text.Collator;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import dk.netarkivet.common.utils.Named;

/**
 * Utilities for handling named objects. Named objects are objects in our datamodel, which have a name and a comment.
 */
public class NamedUtils {

    /**
     * Sorts List of Named objects according to language defined in parameter loc. The sorting is done via a compare
     * function on named objects. The compare function uses Collator for sorting according to language in loc. The
     * compare function is used as Comparator for the Collection Sorting on Named object Lists.
     *
     * @param loc contains the language sorting must adhere to
     * @param list contains list to be sorted. Objects in the List must implement Named
     */
    public static <T extends Named> void sortNamedObjectList(final Locale loc, List<T> list) {
        Collections.sort(list, new Comparator<Named>() {
            public int compare(Named o1, Named o2) {
                Collator myCollator = Collator.getInstance(loc);
                return myCollator.compare(o1.getName(), o2.getName());
            }
        });
    }

}
