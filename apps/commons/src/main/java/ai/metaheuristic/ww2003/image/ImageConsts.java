/*
 * Metaheuristic, Copyright (C) 2017-2023, Innovation platforms, LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package ai.metaheuristic.ww2003.image;

/**
 * @author Serge
 * Date: 11/23/2019
 * Time: 2:17 PM
 */
public class ImageConsts {
    public static final float MM_IN_INCH = 25.4f;

    public static final int DEFAULT_DPI = 150;
    public static final int MAX_PAGE_WIDTH = 140;
    public static final int MAX_PAGE_HEIGHT = 250;

    public static final double MAX_PAGE_WIDTH_INCHES = MAX_PAGE_WIDTH / MM_IN_INCH;
    public static final double MAX_PAGE_HEIGHT_INCHES = MAX_PAGE_HEIGHT / MM_IN_INCH;

}
