/*
 * Copyright ConsenSys AG.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 */
package org.hyperledger.besu.ethereum.chain;

import org.hyperledger.besu.datatypes.Hash;
import org.hyperledger.besu.ethereum.core.Block;
import org.hyperledger.besu.ethereum.core.BlockHeader;
import org.hyperledger.besu.ethereum.trie.MerkleTrieException;
import org.hyperledger.besu.plugin.services.exception.StorageException;

import java.util.Collection;
import java.util.Optional;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BadBlockManager {
  private static final Logger LOG = LoggerFactory.getLogger(BadBlockManager.class);

  public static final int MAX_BAD_BLOCKS_SIZE = 100;
  private final Cache<Hash, Block> badBlocks =
      CacheBuilder.newBuilder().maximumSize(MAX_BAD_BLOCKS_SIZE).concurrencyLevel(1).build();
  private final Cache<Hash, BlockHeader> badHeaders =
      CacheBuilder.newBuilder().maximumSize(MAX_BAD_BLOCKS_SIZE).concurrencyLevel(1).build();
  private final Cache<Hash, Hash> latestValidHashes =
      CacheBuilder.newBuilder().maximumSize(MAX_BAD_BLOCKS_SIZE).concurrencyLevel(1).build();

  /**
   * Add a new invalid block.
   *
   * @param badBlock the invalid block
   * @param cause the cause detailing why the block is considered invalid
   */
  public void addBadBlock(final Block badBlock, final BadBlockCause cause) {
    // TODO(#6301) Expose bad block with cause through BesuEvents
    if (badBlock != null && !isInternalError(cause)) {
      LOG.debug("Register bad block {} with cause: {}", badBlock.toLogString(), cause);
      this.badBlocks.put(badBlock.getHash(), badBlock);
    }
  }

  public void reset() {
    this.badBlocks.invalidateAll();
    this.badHeaders.invalidateAll();
    this.latestValidHashes.invalidateAll();
  }

  /**
   * Return all invalid blocks
   *
   * @return a collection of invalid blocks
   */
  public Collection<Block> getBadBlocks() {
    return badBlocks.asMap().values();
  }

  @VisibleForTesting
  public Collection<BlockHeader> getBadHeaders() {
    return badHeaders.asMap().values();
  }

  /**
   * Return an invalid block based on the hash
   *
   * @param hash of the block
   * @return an invalid block
   */
  public Optional<Block> getBadBlock(final Hash hash) {
    return Optional.ofNullable(badBlocks.getIfPresent(hash));
  }

  public void addBadHeader(final BlockHeader header, final BadBlockCause cause) {
    // TODO(#6301) Expose bad block header with cause through BesuEvents
    LOG.debug("Register bad block header {} with cause: {}", header.toLogString(), cause);
    badHeaders.put(header.getHash(), header);
  }

  public boolean isBadBlock(final Hash blockHash) {
    return badBlocks.asMap().containsKey(blockHash) || badHeaders.asMap().containsKey(blockHash);
  }

  public void addLatestValidHash(final Hash blockHash, final Hash latestValidHash) {
    this.latestValidHashes.put(blockHash, latestValidHash);
  }

  public Optional<Hash> getLatestValidHash(final Hash blockHash) {
    return Optional.ofNullable(latestValidHashes.getIfPresent(blockHash));
  }

  private boolean isInternalError(final BadBlockCause cause) {
    if (cause.getException().isEmpty()) {
      return false;
    }
    // As new "internal only" types of exception are discovered, add them here.
    Throwable causedBy = cause.getException().get();
    return causedBy instanceof StorageException || causedBy instanceof MerkleTrieException;
  }
}
