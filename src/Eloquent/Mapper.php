<?php


namespace iyoule\Database\Eloquent;


use \InvalidArgumentException;
use Illuminate\Database\Eloquent\Model;
use iyoule\BizSpace\Biz;
use iyoule\Reflection\ReflectionClass;

/**
 * Class Mapper
 * @property
 * @package iyoule\Database\Eloquent
 * @mixin \Eloquent
 */
abstract class Mapper extends Model
{


    /**
     * @var Mapper
     */
    protected $masterMapper = null;


    /**
     * @param $biz
     * @return array
     */
    private function biz2Array($biz)
    {
        if ($biz instanceof Biz) {
            try {
                return $biz->serialize2db();
            } catch (\ReflectionException $e) {
                return [];
            }
        }
        if (is_object($biz) && method_exists('biz', 'toArray')) {
            return $biz->toArray();
        }
        if (!is_array($biz)) {
            throw new \InvalidArgumentException("param is must array");
        }
        return $biz;
    }

    /**
     * @param array|Biz $attributes
     * @return Model
     */
    public function fill($attributes)
    {
        return parent::fill($this->biz2Array($attributes)); // TODO: Change the autogenerated stub
    }


    /**
     * @param string $method
     * @param array $parameters
     * @return mixed
     */
    public function __call($method, $parameters)
    {
        $args = [];
        foreach ($parameters as $parameter) {
            if ($parameter instanceof Biz) {
                try {
                    $args[] = $parameter->serialize2db();
                } catch (\ReflectionException $e) {
                    $args[] = $parameter;
                }
            } else {
                $args[] = $parameter;
            }
        }
        return parent::__call($method, $args); // TODO: Change the autogenerated stub
    }


    /**
     * @param $class
     * @return mixed
     */
    public function toBiz($class)
    {
        try {
            if (!class_exists($class) || !(new ReflectionClass($class))->isSubclassOf(Biz::class)) {
                throw new InvalidArgumentException("classname must be iyoule\\BizSpace\\Biz of class");
            }
            return call_user_func([$class, 'unSerialize'], $this->toArray());
        } catch (\ReflectionException $e) {
            throw new InvalidArgumentException("classname must be iyoule\\BizSpace\\Biz of class");
        }
    }


    /**
     * @return Mapper
     */
    public function master()
    {
        if (empty($this->masterMapper)){
            throw new InvalidArgumentException("masterMapper must be require");
        }
        return $this->masterMapper;
    }


    /**
     * @param $class
     * @return Mapper
     */
    public function newSlaveMapper($class)
    {
        try {
            if (!class_exists($class) || !(($mapper = new $class) instanceof self)) {
                throw new \Exception(sprintf("classname must be %s of class", self::class));
            }
            $mapper->masterMapper = $this;
            return $mapper;
        } catch (\Throwable $e) {
            throw new InvalidArgumentException(sprintf("classname must be %s of class", self::class));
        }
    }

}