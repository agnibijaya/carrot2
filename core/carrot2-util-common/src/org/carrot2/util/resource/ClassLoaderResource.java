package org.carrot2.util.resource;

import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.lang.ObjectUtils;
import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Root;

/**
 * A resource loaded using a class loader. This resource provider caches the content of
 * returned resources and closes the stream handle in {@link #open()}.
 */
@Root(name = "class-loader-resource")
public final class ClassLoaderResource implements Resource
{
    private ClassLoader clazzLoader;

    @Attribute
    private String resource;

    /**
     * For XML serialization/deserialization only, use
     * {@link #ClassLoaderResource(ClassLoader, String)}.
     */
    ClassLoaderResource()
    {
        this(null);
    }

    public ClassLoaderResource(String resource)
    {
        this(Thread.currentThread().getContextClassLoader(), resource);
    }

    public ClassLoaderResource(ClassLoader cl, String resource)
    {
        this.clazzLoader = cl;
        this.resource = resource;
    }

    public InputStream open() throws IOException
    {
        return ResourceUtils.prefetch(clazzLoader.getResourceAsStream(resource));
    }

    @Override
    public boolean equals(Object obj)
    {
        if (obj instanceof ClassLoaderResource)
        {
            return ObjectUtils
                .equals(((ClassLoaderResource) obj).resource, this.resource)
                && ObjectUtils.equals(((ClassLoaderResource) obj).clazzLoader,
                    this.clazzLoader);
        }
        else
        {
            return false;
        }
    }

    @Override
    public int hashCode()
    {
        return ObjectUtils.hashCode(clazzLoader) ^ ObjectUtils.hashCode(resource);
    }

}
