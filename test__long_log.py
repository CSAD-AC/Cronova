from utils import logger

logger.info("Hello World")

sum = 0
for i in range(1, 100):
    logger.debug(f"Debug message {i}")
    logger.info(f"After {sum} times")
    logger.warning(f"Warning message {i}")
    logger.error(f"Error message {i}")
    logger.critical(f"Critical message {i}")
    sum += i

