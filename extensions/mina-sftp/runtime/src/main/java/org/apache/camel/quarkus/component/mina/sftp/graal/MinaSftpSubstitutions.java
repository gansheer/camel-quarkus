/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.quarkus.component.mina.sftp.graal;

import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.annotate.TargetClass;

final class MinaSftpSubstitutions {
}

/**
 * Delete SftpFileSystemProvider from the native image.
 *
 * This class is discovered via ServiceLoader and instantiated at build time,
 * creating SshClient instances with threads which causes native image build failures.
 *
 * camel-mina-sftp uses SftpClient directly and doesn't need the NIO FileSystemProvider API,
 * so removing this class is safe.
 */
@TargetClass(className = "org.apache.sshd.sftp.client.fs.SftpFileSystemProvider")
@Delete
final class DeleteSftpFileSystemProvider {
}
