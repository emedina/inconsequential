/* Copyright (C) 2011 SpringSource
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.grails.datastore.gorm.hibernate;

import java.io.Serializable;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import javax.persistence.FlushModeType;

import org.grails.datastore.gorm.hibernate.query.HibernateQuery;
import org.hibernate.Criteria;
import org.hibernate.HibernateException;
import org.hibernate.LockMode;
import org.hibernate.SessionFactory;
import org.hibernate.criterion.Restrictions;
import org.springframework.core.convert.ConversionService;
import org.springframework.datastore.mapping.core.AbstractAttributeStoringSession;
import org.springframework.datastore.mapping.core.Datastore;
import org.springframework.datastore.mapping.core.Session;
import org.springframework.datastore.mapping.engine.EntityInterceptor;
import org.springframework.datastore.mapping.engine.Persister;
import org.springframework.datastore.mapping.model.MappingContext;
import org.springframework.datastore.mapping.model.PersistentEntity;
import org.springframework.datastore.mapping.query.Query;
import org.springframework.datastore.mapping.transactions.Transaction;
import org.springframework.orm.hibernate3.HibernateCallback;
import org.springframework.orm.hibernate3.HibernateTemplate;

/**
 * Session implementation that wraps a Hibernate {@link org.hibernate.Session}
 * 
 * @author Graeme Rocher
 * @since 1.0
 *
 */
public class HibernateSession extends AbstractAttributeStoringSession implements Session {

	private HibernateTemplate hibernateTemplate;
	private HibernateDatastore datastore;
	private boolean connected = true;

	public HibernateSession(HibernateDatastore hibernateDatastore, SessionFactory sessionFactory) {
		this.hibernateTemplate = new HibernateTemplate(sessionFactory);
		this.datastore = hibernateDatastore;
	}

	@Override
	public void setEntityInterceptors(List<EntityInterceptor> interceptors) {
		// do nothing
	}

	@Override
	public void addEntityInterceptor(EntityInterceptor interceptor) {
		// do nothing
	}


	@Override
	public boolean isConnected() {
		return this.connected ;
	}

	@Override
	public void disconnect() {
		super.disconnect();
		connected = false;
	}

	@Override
	public Transaction beginTransaction() {
		throw new UnsupportedOperationException("Use HibernatePlatformTransactionManager instead");
	}

	@Override
	public MappingContext getMappingContext() {
		return getDatastore().getMappingContext();
	}

	@Override
	public Serializable persist(Object o) {
		return hibernateTemplate.save(o);
	}

	@Override
	public void refresh(Object o) {
		hibernateTemplate.refresh(o);
	}

	@Override
	public void attach(Object o) {
		hibernateTemplate.lock(o, LockMode.NONE);
	}

	@Override
	public void flush() {
		hibernateTemplate.flush();
	}

	@Override
	public void clear() {
		hibernateTemplate.clear();
	}

	@Override
	public void clear(Object o) {
		hibernateTemplate.evict(o);
	}

	@Override
	public boolean contains(Object o) {
		return hibernateTemplate.contains(o);
	}

	@Override
	public void setFlushMode(FlushModeType flushMode) {
		if(flushMode == FlushModeType.AUTO) {
			hibernateTemplate.setFlushMode(HibernateTemplate.FLUSH_AUTO);
		}
		else if(flushMode == FlushModeType.COMMIT) {
			hibernateTemplate.setFlushMode(HibernateTemplate.FLUSH_COMMIT);
		}
	}

	@Override
	public FlushModeType getFlushMode() {
		switch(hibernateTemplate.getFlushMode()) {
			case HibernateTemplate.FLUSH_AUTO: return FlushModeType.AUTO;
			case HibernateTemplate.FLUSH_COMMIT: return FlushModeType.COMMIT;
			case HibernateTemplate.FLUSH_ALWAYS: return FlushModeType.AUTO;
			default: return FlushModeType.AUTO;
		}
		
	}

	@Override
	public void lock(Object o) {
		hibernateTemplate.lock(o, LockMode.UPGRADE);
	}

	@Override
	public void unlock(Object o) {
		// do nothing
	}

	@Override
	public List<Serializable> persist(Iterable objects) {
		List<Serializable> identifiers = new ArrayList<Serializable>();
		for (Object object : objects) {
			identifiers.add( hibernateTemplate.save(object) );
		}
		return identifiers;
	}

	@Override
	public <T> T retrieve(Class<T> type, Serializable key) {
		return hibernateTemplate.get(type, key);
		
	}

	@Override
	public <T> T proxy(Class<T> type, Serializable key) {		
		return hibernateTemplate.load(type, key);
	}

	@Override
	public <T> T lock(Class<T> type, Serializable key) {
		return hibernateTemplate.get(type, key, LockMode.UPGRADE);
	}

	@SuppressWarnings("rawtypes")
	@Override
	public void delete(Iterable objects) {
		Collection list = getIterableAsCollection(objects);
		hibernateTemplate.deleteAll(list);
	}

	Collection getIterableAsCollection(Iterable objects) {
		Collection list;
		if(objects instanceof Collection) {			
			list = (Collection) objects;
		}
		else {
			list = new ArrayList();
			for (Object object : objects) {
				list.add(object);
			}
		}
		return list;
	}

	@Override
	public void delete(Object obj) {
		hibernateTemplate.delete(obj);
	}

	@Override
	public List retrieveAll(final Class type, final Iterable keys) {
		final PersistentEntity persistentEntity = getMappingContext().getPersistentEntity(type.getName());
		final ConversionService conversionService = getMappingContext().getConversionService();
		return hibernateTemplate.execute(new HibernateCallback<List>() {

			@Override
			public List doInHibernate(org.hibernate.Session session)
					throws HibernateException, SQLException {
				return session.createCriteria(type)
								.add(Restrictions.in(persistentEntity
												.getIdentity()
												.getName(), getIterableAsCollection(keys)))
								.list();
				
			}
		});
	}

	@Override
	public List retrieveAll(Class type, Serializable... keys) {
		return retrieveAll(type, Arrays.asList(keys));
	}

	@Override
	public Query createQuery(Class type) {
		final PersistentEntity persistentEntity = getMappingContext().getPersistentEntity(type.getName());
		final Criteria criteria = hibernateTemplate
									.getSessionFactory()
									.getCurrentSession()
									.createCriteria(type);
		return new HibernateQuery(criteria, this, persistentEntity);
	}

	@Override
	public Object getNativeInterface() {
		return hibernateTemplate;
	}

	@Override
	public Persister getPersister(Object o) {
		return null;
	}

	@Override
	public Transaction getTransaction() {
		throw new UnsupportedOperationException("Use HibernatePlatformTransactionManager instead");
	}

	@Override
	public Datastore getDatastore() {
		return this.datastore;
	}

}
