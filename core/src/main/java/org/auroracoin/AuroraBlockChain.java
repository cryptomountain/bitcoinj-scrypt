/**
 * Copyright 2011 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.auroracoin;

import static com.google.common.base.Preconditions.checkState;

import com.google.bitcoin.core.Block;
import com.google.bitcoin.core.BlockChain;
import com.google.bitcoin.core.BlockChainListener;
import com.google.bitcoin.core.NetworkParameters;
import com.google.bitcoin.core.StoredBlock;
import com.google.bitcoin.core.Utils;
import com.google.bitcoin.core.VerificationException;
import com.google.bitcoin.core.Wallet;
import com.google.bitcoin.params.TestNet3Params;
import com.google.bitcoin.store.BlockStore;
import com.google.bitcoin.store.BlockStoreException;

import java.math.BigInteger;
import java.util.List;

/**
 * <p>A BlockChain implements the <i>simplified payment verification</i> mode of the Bitcoin protocol. It is the right
 * choice to use for programs that have limited resources as it won't verify transactions signatures or attempt to store
 * all of the block chain. Really, this class should be called SPVBlockChain but for backwards compatibility it is not.
 * </p>
 */
public class AuroraBlockChain extends BlockChain {
    /**
     * <p>Constructs a BlockChain connected to the given wallet and store. To obtain a {@link Wallet} you can construct
     * one from scratch, or you can deserialize a saved wallet from disk using {@link Wallet#loadFromFile(java.io.File)}
     * </p>
     *
     * <p>For the store, you should use {@link com.google.bitcoin.store.SPVBlockStore} or you could also try a
     * {@link com.google.bitcoin.store.MemoryBlockStore} if you want to hold all headers in RAM and don't care about
     * disk serialization (this is rare).</p>
     */
    public AuroraBlockChain(NetworkParameters params, Wallet wallet, BlockStore blockStore) throws BlockStoreException {
    	super(params,wallet,blockStore);
    }

    /**
     * Constructs a BlockChain that has no wallet at all. This is helpful when you don't actually care about sending
     * and receiving coins but rather, just want to explore the network data structures.
     */
    public AuroraBlockChain(NetworkParameters params, BlockStore blockStore) throws BlockStoreException {
    	super(params, blockStore);
    }

    /**
     * Constructs a BlockChain connected to the given list of listeners and a store.
     */
    public AuroraBlockChain(NetworkParameters params, List<BlockChainListener> wallets,
                      BlockStore blockStore) throws BlockStoreException {
        super(params, wallets, blockStore);
    }

    /**
     * Throws an exception if the blocks difficulty is not correct.
     */
    @Override
    protected void checkDifficultyTransitions(StoredBlock storedPrev, Block nextBlock) throws BlockStoreException, VerificationException {
        checkState(lock.isHeldByCurrentThread());
        Block prev = storedPrev.getHeader();
        
        // Is this supposed to be a difficulty transition point?
        if ((storedPrev.getHeight() + 1) % params.getInterval() != 0) {

            // TODO: Refactor this hack after 0.5 is released and we stop supporting deserialization compatibility.
            // This should be a method of the NetworkParameters, which should in turn be using singletons and a subclass
            // for each network type. Then each network can define its own difficulty transition rules.
            //if (params.getId().equals(TestNet3Params.ID_TESTNET) && nextBlock.getTime().after(testnetDiffDate)) {
            //    checkTestnetDifficulty(storedPrev, prev, nextBlock);
            //    return;
            //}

            // No ... so check the difficulty didn't actually change.
            if (nextBlock.getDifficultyTarget() != prev.getDifficultyTarget())
                throw new VerificationException("Unexpected change in difficulty at height " + storedPrev.getHeight() +
                        ": " + Long.toHexString(nextBlock.getDifficultyTarget()) + " vs " +
                        Long.toHexString(prev.getDifficultyTarget()));
            return;
        }

        // We need to find a block far back in the chain. It's OK that this is expensive because it only occurs every
        // two weeks after the initial block chain download.
        long now = System.currentTimeMillis();
        StoredBlock cursor = blockStore.get(prev.getHash());

        int goBack = params.getRetargetBlockCount(cursor);

        for (int i = 0; i < goBack; i++) {
            if (cursor == null) {
                // This should never happen. If it does, it means we are following an incorrect or busted chain.
                throw new VerificationException(
                        "Difficulty transition point but we did not find a way back to the genesis block.");
            }
            cursor = blockStore.get(cursor.getHeader().getPrevBlockHash());
        }

        long elapsed = System.currentTimeMillis() - now;
        if (elapsed > 50)
            log.info("Difficulty transition traversal took {}msec", elapsed);

        // Check if our cursor is null.  If it is, we've used checkpoints to restore.
        if(cursor == null) return;

        Block blockIntervalAgo = cursor.getHeader();
        log.info("Using block " + cursor.getHeight() + " to calculate next difficulty");
        log.info(cursor.toString());
        int timespan = (int) (prev.getTimeSeconds() - blockIntervalAgo.getTimeSeconds());
        final int targetTimespan = params.getTargetTimespan();
        BigInteger newDifficulty = Utils.decodeCompactBits(prev.getDifficultyTarget());
        //if (AuroraCoinParams.ID_AURORACOIN.equals(params.getId())) 
        {
        	// Auroracoin block difficulty
        	if ((storedPrev.getHeight()+1) < 135)
        		newDifficulty = params.getProofOfWorkLimit();
        	else if ((storedPrev.getHeight()+1) <= 5400)
            //if (pindexLast->nHeight+1 > 5400)
        	{
	        	
	        	int nActualTimespan = timespan;
	        	log.info(" nActualTimespan = " + nActualTimespan + " before bounds\n");        
	
		            int nActualTimespanMax = ((targetTimespan*75)/50);
		            int nActualTimespanMin = ((targetTimespan*50)/75);
		           
		       if (nActualTimespan < nActualTimespanMin)
		           nActualTimespan = nActualTimespanMin;
		       if (nActualTimespan > nActualTimespanMax)
		           nActualTimespan = nActualTimespanMax;
		       
		       log.info("Old diff target: " + newDifficulty.toString(16));
		       newDifficulty = newDifficulty.multiply(BigInteger.valueOf(nActualTimespan));
		       log.info("Times " + nActualTimespan);
		        log.info("    is  " + newDifficulty.toString(16));
		       newDifficulty = newDifficulty.divide(BigInteger.valueOf(targetTimespan));
	           log.info("Div by " + targetTimespan);
		        log.info("    is  " + newDifficulty.toString(16));
        	} else 
        		newDifficulty = checkGravityWell();
        	//{
        	//	static const int64	BlocksTargetSpacing			= 5 * 60; // 5 minutes 
        	//	unsigned int		TimeDaySeconds				= 60 * 60 * 24;
        	//	int64				PastSecondsMin				= TimeDaySeconds * 0.5;
        	//	int64				PastSecondsMax				= TimeDaySeconds * 14;
        	//	uint64				PastBlocksMin				= PastSecondsMin / BlocksTargetSpacing;
        	//	uint64				PastBlocksMax				= PastSecondsMax / BlocksTargetSpacing;	
        	//	
        	//	return GravityWell(pindexLast, pblock, BlocksTargetSpacing, PastBlocksMin, PastBlocksMax);
        	//}
        	
        } 
        //else 
        //{
            // Limit the adjustment step.
	    //    if (timespan < targetTimespan / 4)
	    //        timespan = targetTimespan / 4;
	    //    if (timespan > targetTimespan * 4)
	    //        timespan = targetTimespan * 4;
	
	   //     log.info("Old diff target: " + newDifficulty.toString(16));
	    //    newDifficulty = newDifficulty.multiply(BigInteger.valueOf(timespan));
	    //    log.info("Times " + timespan);
	    //    log.info("    is  " + newDifficulty.toString(16));
	    //    newDifficulty = newDifficulty.divide(BigInteger.valueOf(targetTimespan));
	    //    log.info("Div by " + targetTimespan);
	    //    log.info("    is  " + newDifficulty.toString(16));
        //}
        if (newDifficulty.compareTo(params.getProofOfWorkLimit()) > 0) {
            log.info("Difficulty hit proof of work limit: {}", params.getProofOfWorkLimit().toString(16));
            newDifficulty = params.getProofOfWorkLimit();
            log.info("Setting to: {}", newDifficulty.toString(16));
        } else {
            log.info("Difficulty did not hit proof of work limit: {}", params.getProofOfWorkLimit().toString(16));
        }

        int accuracyBytes = (int) (nextBlock.getDifficultyTarget() >>> 24) - 3;
    	
        // The calculated difficulty is to a higher precision than received, so reduce here.
        BigInteger mask = BigInteger.valueOf(0xFFFFFFL).shiftLeft(accuracyBytes * 8);
        newDifficulty = newDifficulty.and(mask);

        BigInteger receivedDifficulty = nextBlock.getDifficultyTargetAsInteger();
        if (newDifficulty.compareTo(receivedDifficulty) != 0)
            throw new VerificationException("Network provided difficulty bits do not match what was calculated: " +
                    receivedDifficulty.toString(16) + " vs " + newDifficulty.toString(16));
        else {
        	log.info("Network provided difficulty bits match what was calculated: " +
                    receivedDifficulty.toString(16) + " vs " + newDifficulty.toString(16));        
       }
    }

    private BigInteger checkGravityWell() {
    	return params.getProofOfWorkLimit();
    }

/*
 * 

unsigned int static GravityWell(const CBlockIndex* pindexLast, const CBlock *pblock, uint64 TargetBlocksSpacingSeconds, uint64 PastBlocksMin, uint64 PastBlocksMax) {

	const CBlockIndex  *BlockLastSolved				= pindexLast;
	const CBlockIndex  *BlockReading				= pindexLast;
	uint64				PastBlocksMass				= 0;
	int64				PastRateActualSeconds		= 0;
	int64				PastRateTargetSeconds		= 0;
	double				PastRateAdjustmentRatio		= double(1);
	CBigNum				PastDifficultyAverage;
	CBigNum				PastDifficultyAveragePrev;
	double				EventHorizonDeviation;
	double				EventHorizonDeviationFast;
	double				EventHorizonDeviationSlow;
	
    if (BlockLastSolved == NULL || BlockLastSolved->nHeight == 0 || (uint64)BlockLastSolved->nHeight < PastBlocksMin) { return bnProofOfWorkLimit.GetCompact(); }
	
	for (unsigned int i = 1; BlockReading && BlockReading->nHeight > 0; i++) {
		if (PastBlocksMax > 0 && i > PastBlocksMax) { break; }
		PastBlocksMass++;
		
		if (i == 1)	{ PastDifficultyAverage.SetCompact(BlockReading->nBits); }
		else		{ PastDifficultyAverage = ((CBigNum().SetCompact(BlockReading->nBits) - PastDifficultyAveragePrev) / i) + PastDifficultyAveragePrev; }
		PastDifficultyAveragePrev = PastDifficultyAverage;
		
		PastRateActualSeconds			= BlockLastSolved->GetBlockTime() - BlockReading->GetBlockTime();
		PastRateTargetSeconds			= TargetBlocksSpacingSeconds * PastBlocksMass;
		PastRateAdjustmentRatio			= double(1);
		if (PastRateActualSeconds < 0) { PastRateActualSeconds = 0; }
		if (PastRateActualSeconds != 0 && PastRateTargetSeconds != 0) {
		PastRateAdjustmentRatio			= double(PastRateTargetSeconds) / double(PastRateActualSeconds);
		}
		EventHorizonDeviation			= 1 + (0.7084 * pow((double(PastBlocksMass)/double(144)), -1.228));
		EventHorizonDeviationFast		= EventHorizonDeviation;
		EventHorizonDeviationSlow		= 1 / EventHorizonDeviation;
		
		if (PastBlocksMass >= PastBlocksMin) {
			if ((PastRateAdjustmentRatio <= EventHorizonDeviationSlow) || (PastRateAdjustmentRatio >= EventHorizonDeviationFast)) { assert(BlockReading); break; }
		}
		if (BlockReading->pprev == NULL) { assert(BlockReading); break; }
		BlockReading = BlockReading->pprev;
	}
	
	CBigNum bnNew(PastDifficultyAverage);
	if (PastRateActualSeconds != 0 && PastRateTargetSeconds != 0) {
		bnNew *= PastRateActualSeconds;
		bnNew /= PastRateTargetSeconds;
	}
    if (bnNew > bnProofOfWorkLimit) { bnNew = bnProofOfWorkLimit; }
	
    /// debug print
    printf("Difficulty Retarget - Gravity Well\n");
    printf("PastRateAdjustmentRatio = %g\n", PastRateAdjustmentRatio);
    printf("Before: %08x  %s\n", BlockLastSolved->nBits, CBigNum().SetCompact(BlockLastSolved->nBits).getuint256().ToString().c_str());
    printf("After:  %08x  %s\n", bnNew.GetCompact(), bnNew.getuint256().ToString().c_str());
	
	return bnNew.GetCompact();
}



 * 
 */
}
