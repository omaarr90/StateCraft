/**
 * Tensor-network simulator backend implemented with Matrix Product States.
 * <p>
 * {@link com.omaarr90.statecraft.engines.tensornetwork.TensorNetworkEngine}
 * targets shallow circuits on larger qubit counts, using SVD compression and
 * terminal measurement sampling. It is exposed through the engine id
 * {@code tensornetwork}.
 */
package com.omaarr90.statecraft.engines.tensornetwork;
