/*
 * Carrot2 Project
 * Copyright (C) 2002-2004, Dawid Weiss
 * Portions (C) Contributors listed in carrot2.CONTRIBUTORS file.
 * All rights reserved.
 *
 * Refer to the full license file "carrot2.LICENSE"
 * in the root folder of the CVS checkout or at:
 * http://www.cs.put.poznan.pl/dweiss/carrot2.LICENSE
 */
package cern.colt.matrix.impl;

import nni.*;

/**
 * @author Stanislaw Osinski
 * @version $Revision$
 */
public class NNIInterface
{

    /** Are native implementation available? */
    private static boolean nativeBlasAvailable;
    private static boolean nativeLapackAvailable;
    private static boolean suppressNNI;

    private NNIInterface()
    {
        // No instance of this class
    }

    static
    {
        // Try to initialise the native libraries
        try
        {
            BLAS.init();
            nativeBlasAvailable = true;
        }
        catch (Throwable t)
        {
            nativeBlasAvailable = false;
        }

        try
        {
            LAPACK.init();
            nativeLapackAvailable = true;
        }
        catch (Throwable t)
        {
            nativeLapackAvailable = false;
        }
    }

    /**
     * Returns true if the native implementation of the BLAS package has been
     * successfully initialised.
     * 
     * @return true if the native implementation of the BLAS package has been
     *         successfully initialised.
     */
    public static boolean isNativeBlasAvailable()
    {
        return (suppressNNI ? false : nativeBlasAvailable);
    }

    /**
     * Returns true if the native implementation of the LAPACK package has been
     * successfully initialised.
     * 
     * @return true if the native implementation of the LAPACK package has been
     *         successfully initialised.
     */
    public static boolean isNativeLapackAvailable()
    {
        return (suppressNNI ? false : nativeLapackAvailable);
    }
    
    /**
     * Temporiarily suppresses NNI calls.
     * 
     * @param suppress
     */
    public static void suppressNNI(boolean suppress)
    {
        suppressNNI = suppress;
    }
}