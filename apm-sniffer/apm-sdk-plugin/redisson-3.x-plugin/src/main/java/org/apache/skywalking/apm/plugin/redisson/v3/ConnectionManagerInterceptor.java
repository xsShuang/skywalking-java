/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.apache.skywalking.apm.plugin.redisson.v3;

import java.util.Objects;
import org.apache.skywalking.apm.agent.core.context.util.PeerFormat;
import org.apache.skywalking.apm.agent.core.logging.api.ILog;
import org.apache.skywalking.apm.agent.core.logging.api.LogManager;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.EnhancedInstance;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.InstanceMethodsAroundInterceptor;
import org.apache.skywalking.apm.agent.core.plugin.interceptor.enhance.MethodInterceptResult;
import org.apache.skywalking.apm.plugin.redisson.v3.util.ClassUtil;
import org.redisson.config.Config;

import java.lang.reflect.Method;
import java.net.URI;
import java.util.Collection;
import org.redisson.connection.MasterSlaveConnectionManager;
import org.redisson.connection.ServiceManager;

public class ConnectionManagerInterceptor implements InstanceMethodsAroundInterceptor {

    private static final ILog LOGGER = LogManager.getLogger(ConnectionManagerInterceptor.class);

    @Override
    public void beforeMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
                             MethodInterceptResult result) throws Throwable {
    }

    @Override
    public Object afterMethod(EnhancedInstance objInst, Method method, Object[] allArguments, Class<?>[] argumentsTypes,
                              Object ret) throws Throwable {
        try {
            Config config = getConfig(objInst);
            Object singleServerConfig = null;
            Object sentinelServersConfig = null;
            Object masterSlaveServersConfig = null;
            Object clusterServersConfig = null;
            Object replicatedServersConfig = null;
            if (Objects.nonNull(config)) {
                singleServerConfig = ClassUtil.getObjectField(config, "singleServerConfig");
                sentinelServersConfig = ClassUtil.getObjectField(config, "sentinelServersConfig");
                masterSlaveServersConfig = ClassUtil.getObjectField(config, "masterSlaveServersConfig");
                clusterServersConfig = ClassUtil.getObjectField(config, "clusterServersConfig");
                replicatedServersConfig = ClassUtil.getObjectField(config, "replicatedServersConfig");
            }

            StringBuilder peer = new StringBuilder();
            EnhancedInstance retInst = (EnhancedInstance) ret;

            if (singleServerConfig != null) {
                Object singleAddress = ClassUtil.getObjectField(singleServerConfig, "address");
                peer.append(getPeer(singleAddress));
                retInst.setSkyWalkingDynamicField(PeerFormat.shorten(peer.toString()));
                return ret;
            }
            if (sentinelServersConfig != null) {
                appendAddresses(peer, (Collection) ClassUtil.getObjectField(sentinelServersConfig, "sentinelAddresses"));
                retInst.setSkyWalkingDynamicField(PeerFormat.shorten(peer.toString()));
                return ret;
            }
            if (masterSlaveServersConfig != null) {
                Object masterAddress = ClassUtil.getObjectField(masterSlaveServersConfig, "masterAddress");
                peer.append(getPeer(masterAddress)).append(";");
                appendAddresses(peer, (Collection) ClassUtil.getObjectField(masterSlaveServersConfig, "slaveAddresses"));
                retInst.setSkyWalkingDynamicField(PeerFormat.shorten(peer.toString()));
                return ret;
            }
            if (clusterServersConfig != null) {
                appendAddresses(peer, (Collection) ClassUtil.getObjectField(clusterServersConfig, "nodeAddresses"));
                retInst.setSkyWalkingDynamicField(PeerFormat.shorten(peer.toString()));
                return ret;
            }
            if (replicatedServersConfig != null) {
                appendAddresses(peer, (Collection) ClassUtil.getObjectField(replicatedServersConfig, "nodeAddresses"));
                retInst.setSkyWalkingDynamicField(PeerFormat.shorten(peer.toString()));
                return ret;
            }
        } catch (Exception e) {
            LOGGER.warn("redisClient set peer error: ", e);
        }
        return ret;
    }

    private void appendAddresses(StringBuilder peer, Collection nodeAddresses) {
        if (nodeAddresses != null && !nodeAddresses.isEmpty()) {
            for (Object uri : nodeAddresses) {
                peer.append(getPeer(uri)).append(";");
            }
        }
    }

    /**
     * In some high versions of redisson, such as 3.11.1. The attribute address in the RedisClientConfig class is
     * changed from the lower version of the URI to the String. So use the following code for compatibility.
     *
     * @param obj Address object
     * @return the sw peer
     */
    static String getPeer(Object obj) {
        if (obj instanceof String) {
            return ((String) obj).replace("redis://", "");
        } else if (obj instanceof URI) {
            URI uri = (URI) obj;
            return uri.getHost() + ":" + uri.getPort();
        } else {
            LOGGER.warn("redisson not support this version");
            return null;
        }
    }

    private Config getConfig(EnhancedInstance objInst) {
        Config config = null;
        MasterSlaveConnectionManager connectionManager = (MasterSlaveConnectionManager) objInst;
        try {
            config = (Config) ClassUtil.getObjectField(connectionManager, "cfg");
        } catch (NoSuchFieldException | IllegalAccessException ignore) {
            try {
                ServiceManager serviceManager = (ServiceManager) ClassUtil.getObjectField(
                    connectionManager, "serviceManager");
                config = serviceManager.getCfg();
            } catch (NoSuchFieldException | IllegalAccessException ignore2) {
            }
        }
        return config;
    }

    @Override
    public void handleMethodException(EnhancedInstance objInst, Method method, Object[] allArguments,
                                      Class<?>[] argumentsTypes, Throwable t) {
    }
}
