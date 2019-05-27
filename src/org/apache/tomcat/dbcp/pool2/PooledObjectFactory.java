package org.apache.tomcat.dbcp.pool2;

/**
 * 用于定义由{@link ObjectPool}提供服务的实例的生命周期方法.
 * <p>
 * 当{@link ObjectPool}委托给{@link PooledObjectFactory}时,
 * <ol>
 *  <li>
 *   只要需要新实例, 就会调用{@link #makeObject}.
 *  </li>
 *  <li>
 *   {@link #activateObject}在已被{@link #passivateObject passivated}的每个实例上调用, 在对象从池中{@link ObjectPool#borrowObject borrowed}之前
 *  </li>
 *  <li>
 *   可以在{@link #activateObject activated}实例上调用{@link #validateObject}, 以确保它们可以从池中{@link ObjectPool#borrowObject borrowed}.
 *   {@link #validateObject}也可用于在{@link #passivateObject passivated}之前测试{@link ObjectPool#returnObject returned}池的实例.
 *   它只会在激活的实例上调用.
 *  </li>
 *  <li>
 *   返回池时, 会在每个实例上调用{@link #passivateObject}.
 *  </li>
 *  <li>
 *   当从池中“删除”时, 会在每个实例上调用{@link #destroyObject} (是否由于{@link #validateObject}的响应, 或者特定于池实现的原因).
 *   无法保证被销毁的实例将被视为主动，被动或大致一致的状态.
 *  </li>
 * </ol>
 * {@link PooledObjectFactory} 必须是线程安全的. {@link ObjectPool}唯一的承诺是, 对象的同一个实例不会一次传递给<code>PoolableObjectFactory</code>的多个方法.
 * <p>
 * {@link KeyedObjectPool}的客户端借用并返回基础值类型{@code V}的实例时, 工厂方法作用于{@link PooledObject PooledObject&lt;V&gt;}实例.
 * 这些是池用于跟踪和维护有关其管理的对象的状态信息的对象包装器.
 *
 * @param <T> 此工厂中管理的元素类型.
 */
public interface PooledObjectFactory<T> {
  /**
   * 创建一个可由池服务的实例，并将其封装在池中管理的{@link PooledObject}中.
   *
   * @return 一个{@code PooledObject}封装一个可以由池服务的实例
   *
   * @throws Exception 如果创建新实例时出现问题, 这将传播到请求对象的代码.
   */
  PooledObject<T> makeObject() throws Exception;

  /**
   * 销毁池不再需要的实例.
   * <p>
   * 对于此方法的实现而言, 重要的是要意识到无法保证<code>obj</code>将处于什么状态, 并且应该准备好实现来处理意外错误.
   * <p>
   * 此外, 实现必须考虑到丢失到垃圾收集器的实例可能永远不会被销毁.
   * </p>
   *
   * @param p 要销毁的{@code PooledObject}封装的实例
   *
   * @throws Exception 应该避免, 因为它可能被池实现忽略.
   */
  void destroyObject(PooledObject<T> p) throws Exception;

  /**
   * 确保池可以安全地返回实例.
   *
   * @param p 要验证的{@code PooledObject}封装的实例
   *
   * @return <code>false</code> 如果<code>obj</code>是无效的, 而且应该从池中删除; 否则<code>true</code>.
   */
  boolean validateObject(PooledObject<T> p);

  /**
   * 重新初始化池返回的实例.
   *
   * @param p 要激活的{@code PooledObject}封装的实例
   *
   * @throws Exception 如果激活<code>obj</code>时出现问题, 则池可能会忽略此异常.
   */
  void activateObject(PooledObject<T> p) throws Exception;

  /**
   * 取消初始化要返回到空闲对象池的实例.
   *
   * @param p 要钝化的{@code PooledObject}封装的实例
   *
   * @throws Exception 如果钝化<code>obj</code>时出现问题, 则池可能会忽略此异常.
   */
  void passivateObject(PooledObject<T> p) throws Exception;
}
